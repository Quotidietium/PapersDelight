package dev.tako.papersdelight.mechanic.basket;

import net.momirealms.craftengine.bukkit.block.entity.SimpleStorageBlockEntityController;
import dev.tako.papersdelight.ce.CraftEngineUtil;
import cn.chengzhimeow.ccscheduler.scheduler.CCScheduler;
import cn.chengzhimeow.ccscheduler.task.CCTask;
import net.momirealms.craftengine.core.world.BlockPos;
import net.momirealms.craftengine.core.block.entity.BlockEntity;
import net.momirealms.craftengine.core.world.chunk.CEChunk;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.BoundingBox;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class BasketManager implements Listener {

    private static final CCScheduler SCHEDULER = CCScheduler.getInstance();

    private final JavaPlugin plugin;
    static final int MAX_BASKETS_PER_TICK = 32;
    private static final int MAX_EMPTY_BACKOFF_TICKS = 32;

    private final Set<Location> knownBaskets = ConcurrentHashMap.newKeySet();
    private final Map<Location, Long> nextAttempts = new ConcurrentHashMap<>();
    private final Map<Location, Integer> emptyBackoffs = new ConcurrentHashMap<>();
    private CCTask tickTask;
    private int collectCooldownTicks = 8;
    private volatile long currentTick;

    public BasketManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        if (tickTask != null) tickTask.cancel();
        collectCooldownTicks = Math.max(1, BasketBlockBehavior.getCollectInterval());
        currentTick = 0;
        tickTask = SCHEDULER.getGlobalRegionScheduler().runTaskTimer(plugin, 1L, 1L, this::collectTick);
    }

    public void stopAll() {
        if (tickTask != null) tickTask.cancel();
        knownBaskets.clear();
        nextAttempts.clear();
        emptyBackoffs.clear();
        currentTick = 0;
    }

    private void collectTick() {
        currentTick++;
        Map<Long, List<Location>> dueByChunk = null;
        int due = 0;
        for (Location loc : knownBaskets) {
            if (due >= MAX_BASKETS_PER_TICK) break;
            if (nextAttempts.getOrDefault(loc, 0L) > currentTick) continue;
            if (dueByChunk == null) dueByChunk = new HashMap<>();
            dueByChunk.computeIfAbsent(chunkKey(loc), k -> new ArrayList<>(1)).add(loc);
            due++;
        }
        if (dueByChunk == null) return;
        for (List<Location> group : dueByChunk.values()) {
            SCHEDULER.getRegionScheduler().runTask(plugin, group.get(0), () -> group.forEach(this::collectAt));
        }
    }

    private static long chunkKey(Location loc) {
        int worldMix = loc.getWorld() != null
                ? (int) (loc.getWorld().getUID().getLeastSignificantBits() >>> 48) : 0;
        return ((long) ((loc.getBlockX() >> 4) ^ worldMix)) << 32 | ((long) (loc.getBlockZ() >> 4)) & 0xFFFFFFFFL;
    }

    private void collectAt(Location loc) {
        Block block = loc.getBlock();
        if (!block.getWorld().isChunkLoaded(block.getX() >> 4, block.getZ() >> 4)) return;
        if (!BasketBlockBehavior.isBasket(block)) {
            if (block.getType().isAir()) {
                knownBaskets.remove(loc);
                nextAttempts.remove(loc);
                emptyBackoffs.remove(loc);
            } else {
                scheduleRetry(loc, collectCooldownTicks);
            }
            return;
        }
        if (!BasketBlockBehavior.isEnabled(block)) {
            scheduleRetry(loc, collectCooldownTicks);
            return;
        }

        Inventory inv = getInventory(block);
        if (inv == null) {
            scheduleRetry(loc, collectCooldownTicks);
            return;
        }

        String facing = BasketBlockBehavior.getFacing(block);
        BlockFace face = toBlockFace(facing);
        Block targetBlock = block.getRelative(face);

            BoundingBox area = BoundingBox.of(block).union(BoundingBox.of(targetBlock));
            boolean collected = false;
            for (org.bukkit.entity.Entity entity : block.getWorld().getNearbyEntities(area, e -> e instanceof Item)) {
                Item item = (Item) entity;
                if (!item.isValid() || item.isDead()) continue;
                ItemStack stack = item.getItemStack();
                if (stack.isEmpty()) continue;

                ItemStack remainder = insertInto(inv, stack);
                if (remainder.isEmpty()) {
                    item.remove();
                    markChunkUnsaved(block);
                    collected = true;
                } else if (remainder.getAmount() < stack.getAmount()) {
                    item.setItemStack(remainder);
                    markChunkUnsaved(block);
                    collected = true;
                }
                break;
            }
            int currentBackoff = emptyBackoffs.getOrDefault(loc, 0);
            int delay = nextCollectionDelay(collected, currentBackoff, collectCooldownTicks);
            if (collected) {
                emptyBackoffs.remove(loc);
            } else {
                emptyBackoffs.put(loc, delay);
            }
            scheduleRetry(loc, delay);
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        Block block = event.getClickedBlock();
        if (block == null) return;
        if (!BasketBlockBehavior.isBasket(block)) return;

        Player player = event.getPlayer();
        if (!player.isSneaking()) {

            player.swingMainHand();
        } else if (player.getInventory().getItemInMainHand().isEmpty()) {

            player.swingMainHand();
        }

    }

    static void updateRedstoneState(Block block) {
        boolean powered = block.isBlockPowered();
        String currentEnabled = CraftEngineUtil.getCustomBlockProperty(block, "enabled");
        String expected = powered ? "false" : "true";
        if (!expected.equals(currentEnabled)) {
            CraftEngineUtil.setCustomBlockProperty(block, "enabled", expected);
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();

        SCHEDULER.getRegionScheduler().runTaskLater(plugin, block.getLocation(), 1L, () -> {
            if (BasketBlockBehavior.isBasket(block)) {
                registerBasket(block.getLocation().toBlockLocation());
            }
        });
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {

        if (event.isNewChunk()) return;
        World world = event.getChunk().getWorld();
        int cx = event.getChunk().getX(), cz = event.getChunk().getZ();
        SCHEDULER.getRegionScheduler().runTaskLater(plugin, world, cx, cz, 5L, () -> {
            if (!world.isChunkLoaded(cx, cz)) return;
            scanChunk(world.getChunkAt(cx, cz));
        });
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        int cx = event.getChunk().getX(), cz = event.getChunk().getZ();
        World world = event.getWorld();
        knownBaskets.removeIf(loc -> {
            if (loc.getWorld() == world && (loc.getBlockX() >> 4) == cx && (loc.getBlockZ() >> 4) == cz) {
                nextAttempts.remove(loc);
                emptyBackoffs.remove(loc);
                return true;
            }
            return false;
        });
    }

    public void discoverAllBaskets() {
        List<Chunk> all = new ArrayList<>();
        for (World w : Bukkit.getWorlds()) all.addAll(List.of(w.getLoadedChunks()));
        if (all.isEmpty()) return;
        scanBatch(all, 0);
    }

    private void scanBatch(List<Chunk> chunks, int start) {
        int end = Math.min(start + 4, chunks.size());
        for (int i = start; i < end; i++) {
            Chunk chunk = chunks.get(i);
            SCHEDULER.getRegionScheduler().runTask(plugin, chunk.getWorld(), chunk.getX(), chunk.getZ(), () -> scanChunk(chunk));
        }
        if (end < chunks.size()) {
            SCHEDULER.getGlobalRegionScheduler().runTaskLater(plugin, 2L, () -> scanBatch(chunks, end));
        }
    }

    private void scanChunk(Chunk chunk) {
        if (!chunk.isLoaded()) return;
        Set<String> ids = BasketBlockBehavior.getRegisteredBlockIds();
        if (ids.isEmpty()) return;
        World world = chunk.getWorld();
        CEChunk ceChunk = CraftEngineUtil.getLoadedChunk(world, chunk.getX(), chunk.getZ());
        if (ceChunk == null) return;

        for (BlockEntity entity : ceChunk.blockEntities()) {
            String id = CraftEngineUtil.getBlockEntityId(entity);
            if (id == null || !ids.contains(id)) continue;
            BlockPos pos = entity.pos();
            registerBasket(world.getBlockAt(pos.x(), pos.y(), pos.z()).getLocation().toBlockLocation());
        }
    }

    private void registerBasket(Location loc) {
        if (knownBaskets.add(loc)) {
            nextAttempts.remove(loc);
            emptyBackoffs.remove(loc);
        }
    }

    private void scheduleRetry(Location loc, int delay) {
        nextAttempts.put(loc, currentTick + Math.max(1, delay));
    }

    static int nextEmptyBackoff(int current) {
        if (current <= 0) return 1;
        return Math.min(MAX_EMPTY_BACKOFF_TICKS, current * 2);
    }

    static int nextCollectionDelay(boolean collected, int currentEmptyBackoff, int collectCooldown) {
        return collected ? Math.max(1, collectCooldown) : nextEmptyBackoff(currentEmptyBackoff);
    }

    private Inventory getInventory(Block block) {
        try {
            var ceWorld = CraftEngineUtil.getLoadedWorld(block.getWorld());
            if (ceWorld == null) return null;
            var cePos = new BlockPos(block.getX(), block.getY(), block.getZ());
            BlockEntity be = ceWorld.getBlockEntityAtIfLoaded(cePos);
            if (be == null) return null;
            var ref = new InventoryRef();
            be.controller.let(SimpleStorageBlockEntityController.class, c -> ref.inv = c.inventory());
            return ref.inv;
        } catch (Exception e) {
            return null;
        }
    }

    private static final class InventoryRef {
        Inventory inv;
    }

    private static ItemStack insertInto(Inventory inv, ItemStack stack) {
        ItemStack remaining = stack.clone();
        ItemStack[] contents = inv.getStorageContents();

        for (int i = 0; i < contents.length && !remaining.isEmpty(); i++) {
            ItemStack slot = contents[i];
            if (slot == null || slot.isEmpty() || !slot.isSimilar(remaining)) continue;
            int maxStack = Math.min(slot.getMaxStackSize(), inv.getMaxStackSize());
            int space = maxStack - slot.getAmount();
            if (space <= 0) continue;
            int moved = Math.min(space, remaining.getAmount());
            slot.setAmount(slot.getAmount() + moved);
            remaining.setAmount(remaining.getAmount() - moved);
        }

        for (int i = 0; i < contents.length && !remaining.isEmpty(); i++) {
            if (contents[i] != null && !contents[i].isEmpty()) continue;
            contents[i] = remaining.clone();
            remaining = ItemStack.empty();
        }

        inv.setStorageContents(contents);
        return remaining.isEmpty() ? ItemStack.empty() : remaining;
    }

    private static BlockFace toBlockFace(String facing) {
        return switch (facing) {
            case "down" -> BlockFace.DOWN;
            case "north" -> BlockFace.NORTH;
            case "south" -> BlockFace.SOUTH;
            case "east" -> BlockFace.EAST;
            case "west" -> BlockFace.WEST;
            default -> BlockFace.UP;
        };
    }

    private static void markChunkUnsaved(Block block) {
        try {
            var ceWorld = CraftEngineUtil.getLoadedWorld(block.getWorld());
            if (ceWorld == null) return;
            var cePos = new BlockPos(block.getX(), block.getY(), block.getZ());
            BlockEntity be = ceWorld.getBlockEntityAtIfLoaded(cePos);
            if (be != null) {
                var chunk = be.world().getChunkAtIfLoaded(cePos);
                if (chunk != null) chunk.setUnsaved(true);
            }
        } catch (Exception ignored) {
        }
    }
}
