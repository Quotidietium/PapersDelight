package dev.tako.papersdelight.mechanic.stove;

import dev.tako.papersdelight.bridge.NMSHelper;
import dev.tako.papersdelight.common.TickBatch;
import dev.tako.papersdelight.config.ConfigManager;
import dev.tako.papersdelight.config.StoveConfig;
import dev.tako.papersdelight.ce.CraftEngineUtil;
import cn.chengzhimeow.ccscheduler.scheduler.CCScheduler;
import dev.tako.papersdelight.util.ParticleVisibility;
import net.momirealms.craftengine.bukkit.util.LocationUtils;
import net.momirealms.craftengine.core.block.entity.BlockEntity;
import net.momirealms.craftengine.core.world.BlockPos;
import net.momirealms.craftengine.core.world.CEWorld;
import net.momirealms.craftengine.core.world.chunk.CEChunk;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class StoveManager implements Listener {

    private static final CCScheduler SCHEDULER = CCScheduler.getInstance();

    private final JavaPlugin plugin;

    static volatile StoveManager instance;
    private static volatile StoveConfig.AmbientSound ambientSound = StoveConfig.AmbientSound.defaults();

    final Set<Location> knownStoves = ConcurrentHashMap.newKeySet();

    private final Map<Location, ItemDisplay[]> displayEntities = new ConcurrentHashMap<>();
    private final Set<Location> particleStoves = ConcurrentHashMap.newKeySet();

    private final Set<Location> restored = ConcurrentHashMap.newKeySet();

    private boolean initialScanDone;

    private volatile StoveConfig config;

    private static final float[][] FD_OFFSETS = {
            { 0.3f,  0.2f}, { 0.0f,  0.2f}, {-0.3f,  0.2f},
            { 0.3f, -0.2f}, { 0.0f, -0.2f}, {-0.3f, -0.2f},
    };

    public StoveManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        synchronized (StoveManager.class) {

            config = StoveConfig.load(ambientSound);
            instance = this;
        }
        initialScanDone = false;
    }

    public void stopAll() {
        for (ItemDisplay[] arr : displayEntities.values()) {
            for (ItemDisplay d : arr) {
                if (d == null || !d.isValid()) continue;
                if (!plugin.isEnabled()) {
                    try { d.remove(); } catch (Throwable ignored) { }
                    continue;
                }
                SCHEDULER.getEntityScheduler().runTask(plugin, d, d::remove);
            }
        }
        displayEntities.clear();
        knownStoves.clear();
        restored.clear();
        particleStoves.clear();
    }

    public void discoverAllStoves() {
        List<Chunk> all = new ArrayList<>();
        for (World w : Bukkit.getWorlds()) all.addAll(List.of(w.getLoadedChunks()));
        if (all.isEmpty()) {
            initialScanDone = true;
            return;
        }
        scanBatch(all, 0);
    }

    private void scanBatch(List<Chunk> chunks, int start) {
        int end = Math.min(start + 4, chunks.size());
        for (int i = start; i < end; i++) {
            Chunk c = chunks.get(i);
            SCHEDULER.getRegionScheduler().runTask(plugin, c.getWorld(), c.getX(), c.getZ(), () -> scanChunk(c.getWorld(), c.getX(), c.getZ()));
        }
        if (end < chunks.size()) {
            SCHEDULER.getGlobalRegionScheduler().runTaskLater(plugin, 2L, () -> scanBatch(chunks, end));
        } else {
            initialScanDone = true;
        }
    }

    void tickStove(StoveBlockEntityController ctrl, CEWorld ceWorld, BlockPos cePos) {
        if (!plugin.isEnabled()) return;
        Block block = bukkitBlock(ceWorld, cePos);
        if (block == null) return;
        Location loc = block.getLocation().toBlockLocation();
        if (block.getType().isAir()) {
            knownStoves.remove(loc);
            particleStoves.remove(loc);
            removeAllDisplayEntities(loc);
            return;
        }

        int now = Bukkit.getCurrentTick();
        int elapsed = TickBatch.due(ctrl.lastPassTick, now, TickBatch.interval());
        if (elapsed == 0) return;
        ctrl.lastPassTick = now;

        StoveConfig cfg = config;
        boolean lit = StoveBlockBehavior.isLit(block);
        if (lit) particleStoves.add(loc);
        else particleStoves.remove(loc);
        if (!lit && ctrl.isEmpty()) return;

        // R7：dropLoc 惰性分配（仅真正产生掉落物时），blockedAbove 只在非空时检查——
        // 点燃但空/未点燃的炉子不再每 tick 付一次 getRelative+getType+Location 克隆
        Location dropLoc = null;

        for (int i = 0; i < elapsed; i++) {
            if (lit) {
                if (--ctrl.particleCountdown <= 0) {
                    ctrl.particleCountdown = jitteredInterval(cfg.particleIntervalTicks);
                    if (ParticleVisibility.hasNearbyViewer(block, cfg.particleViewDistance)) {
                        particleTickActive(block, loc, cfg);
                    }
                }
                if (--ctrl.ambientCountdown <= 0) {
                    ctrl.ambientCountdown = jitteredInterval(ambientDelay(cfg.ambientSound));
                    if (ParticleVisibility.hasNearbyViewer(block, cfg.ambientSoundViewDistance)) {
                        ambientSoundTick(block, loc, cfg);
                    }
                }
            }
            if (ctrl.isEmpty()) continue;
            if (isBlockedAbove(block)) {
                if (dropLoc == null) dropLoc = loc.clone().add(0.5, 1.02, 0.5);
                for (ItemStack item : ctrl.takeAllItems()) {
                    block.getWorld().dropItem(dropLoc, item);
                }
                removeAllDisplayEntities(loc);
                break;
            }
            List<StoveBlockEntityController.CompletedSlot> completed = ctrl.serverTick(lit);
            if (completed.isEmpty()) continue;
            if (dropLoc == null) dropLoc = loc.clone().add(0.5, 1.02, 0.5);
            for (var cs : completed) {
                block.getWorld().dropItem(dropLoc, cs.result());
                removeDisplayEntity(loc, cs.slot());
            }
        }
    }

    private static boolean isBlockedAbove(Block block) {
        Material aboveMat = block.getRelative(0, 1, 0).getType();
        return aboveMat != Material.AIR && aboveMat != Material.CAVE_AIR
                && aboveMat != Material.VOID_AIR;
    }

    private static long jitteredInterval(long intervalTicks) {
        long jitter = intervalTicks / 2;
        return Math.max(1L, intervalTicks + ThreadLocalRandom.current().nextLong(-jitter, jitter + 1));
    }

    private static long ambientDelay(StoveConfig.AmbientSound sound) {
        return (long) (ThreadLocalRandom.current().nextFloat()
                * (sound.intervalMax() - sound.intervalMin()) + sound.intervalMin());
    }

    void forgetStove(StoveBlockEntityController ctrl) {
        CEWorld ceWorld = ctrl.blockEntity().world();
        if (ceWorld == null) return;
        BlockPos pos = ctrl.blockEntity().pos();
        Block block = bukkitBlock(ceWorld, pos);
        if (block == null) return;
        Location loc = block.getLocation().toBlockLocation();
        knownStoves.remove(loc);
        restored.remove(loc);
        particleStoves.remove(loc);
        removeAllDisplayEntities(loc);
    }

    private static Block bukkitBlock(CEWorld ceWorld, BlockPos pos) {
        World world = Bukkit.getWorld(ceWorld.name());
        return world == null ? null : world.getBlockAt(pos.x(), pos.y(), pos.z());
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        World world = event.getChunk().getWorld();
        int cx = event.getChunk().getX(), cz = event.getChunk().getZ();

        SCHEDULER.getRegionScheduler().runTaskLater(plugin, world, cx, cz, 1L, () -> {
            if (!world.isChunkLoaded(cx, cz)) return;
            scanChunk(world, cx, cz);
        });
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        int cx = event.getChunk().getX(), cz = event.getChunk().getZ();
        World world = event.getWorld();
        for (Iterator<Location> it = knownStoves.iterator(); it.hasNext(); ) {
            Location loc = it.next();
            if (loc.getWorld() == world && (loc.getBlockX() >> 4) == cx && (loc.getBlockZ() >> 4) == cz) {
                it.remove();
                restored.remove(loc);
                particleStoves.remove(loc);
                removeAllDisplayEntities(loc);
            }
        }
    }

    private void scanChunk(World world, int chunkX, int chunkZ) {
        if (!world.isChunkLoaded(chunkX, chunkZ)) return;
        CEChunk chunk = CraftEngineUtil.getLoadedChunk(world, chunkX, chunkZ);
        if (chunk == null) return;

        for (BlockEntity entity : chunk.blockEntities()) {
            StoveBlockEntityController ctrl = getController(entity);
            if (ctrl == null) continue;
            BlockPos pos = entity.pos();
            Block block = world.getBlockAt(pos.x(), pos.y(), pos.z());
            Location blockLoc = block.getLocation().toBlockLocation();
            if (!knownStoves.add(blockLoc)) continue;
            if (!restored.contains(blockLoc)) {
                restored.add(blockLoc);
                restoreDisplayEntities(ctrl, block);
            }
        }
    }

    private void restoreDisplayEntities(StoveBlockEntityController ctrl, Block block) {
        ItemStack[] items = ctrl.getItems();
        for (int i = 0; i < StoveBlockEntityController.SLOT_COUNT; i++) {
            if (!items[i].isEmpty()) spawnDisplayEntity(block, i, items[i]);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();
        if (getController(block) == null) return;
        knownStoves.add(block.getLocation().toBlockLocation());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();

        Location loc = block.getLocation().toBlockLocation();
        knownStoves.remove(loc);
        restored.remove(loc);
        particleStoves.remove(loc);

        StoveBlockEntityController ctrl = getController(block);
        if (ctrl == null) return;

        Location dropLoc = block.getLocation().clone().add(0.5, 0.5, 0.5);
        for (ItemStack item : ctrl.takeAllItems()) {
            block.getWorld().dropItemNaturally(dropLoc, item);
        }
        removeAllDisplayEntities(loc);
    }

    void spawnDisplayEntity(Block block, int slot, ItemStack item) {
        StoveConfig cfg = config;
        Location blockLoc = block.getLocation().toBlockLocation();
        ItemDisplay[] positions = displayEntities.computeIfAbsent(blockLoc,
                k -> new ItemDisplay[StoveBlockEntityController.SLOT_COUNT]);
        if (positions[slot] != null && positions[slot].isValid()) positions[slot].remove();

        Location spawnLoc = getSlotWorldPosition(block, slot, cfg);
        if (spawnLoc == null) return;

        float yawRad = (float) Math.toRadians(getDisplayYaw(StoveBlockBehavior.getFacing(block)));

        ItemStack visual = item.clone();
        visual.setAmount(1);

        ItemDisplay display = spawnLoc.getWorld().spawn(spawnLoc, ItemDisplay.class, d -> {
            d.setTransformation(new Transformation(
                    new Vector3f(0, 0, 0),
                    new Quaternionf().rotateY(yawRad).rotateX((float) Math.toRadians(cfg.displayPitch)),
                    new Vector3f(cfg.displayScale),
                    new Quaternionf()
            ));
            d.setItemStack(visual);
            d.setPersistent(false);
            d.setVisibleByDefault(true);
        });
        positions[slot] = display;
    }

    private void removeDisplayEntity(Location loc, int slot) {
        ItemDisplay[] positions = displayEntities.get(loc.toBlockLocation());
        if (positions == null || slot < 0 || slot >= positions.length) return;
        if (positions[slot] != null && positions[slot].isValid()) positions[slot].remove();
        positions[slot] = null;
    }

    private void removeAllDisplayEntities(Location loc) {
        ItemDisplay[] arr = displayEntities.remove(loc.toBlockLocation());
        if (arr != null) for (ItemDisplay d : arr) {
            if (d != null && d.isValid()) d.remove();
        }
    }

    private Location getSlotWorldPosition(Block block, int slot, StoveConfig cfg) {
        float[] off = FD_OFFSETS[slot];
        float itemX = off[0], itemZ = off[1];
        String facing = StoveBlockBehavior.getFacing(block);

        if ("east".equals(facing) || "west".equals(facing)) {
            float tmp = itemX;
            itemX = itemZ;
            itemZ = tmp;
        }

        int stepX = 0, stepZ = 0, cwX = 0, cwZ = 0;
        switch (facing) {
            case "north": stepZ = -1; cwX = 1;  break;
            case "south": stepZ =  1; cwX = -1; break;
            case "west":  stepX = -1; cwZ = -1; break;
            case "east":  stepX =  1; cwZ = 1;  break;
        }

        double wx = (block.getX() + 0.5) - (stepX * itemX) + (cwX * itemX);
        double wz = (block.getZ() + 0.5) - (stepZ * itemZ) + (cwZ * itemZ);

        return new Location(block.getWorld(), wx, block.getY() + cfg.displayBaseY, wz);
    }

    public void activateStove(Block block) {
        knownStoves.add(block.getLocation().toBlockLocation());
    }

    private int countNearbyParticleTasks(Location loc) {
        int cx = loc.getBlockX() >> 4;
        int cz = loc.getBlockZ() >> 4;
        int count = 0;
        for (Location other : particleStoves) {
            if ((other.getBlockX() >> 4) == cx && (other.getBlockZ() >> 4) == cz) count++;
        }
        return count;
    }

    private void particleTickActive(Block block, Location loc, StoveConfig cfg) {
        if (!StoveBlockBehavior.isLit(block)) return;

        int nearby = countNearbyParticleTasks(loc);
        if (dev.tako.papersdelight.util.ParticleThrottle.shouldSkip(
                nearby, cfg.particleThrottle.threshold, cfg.particleThrottle.maxRate)) return;
        World world = block.getWorld();
        String facing = StoveBlockBehavior.getFacing(block);

        if (!isFrontBlocked(block, facing)) {
            double cx = loc.getX() + 0.5, cy = loc.getY(), cz = loc.getZ() + 0.5;

            boolean xAxis = "east".equals(facing) || "west".equals(facing);
            int stepX = "east".equals(facing) ? 1 : "west".equals(facing) ? -1 : 0;
            int stepZ = "south".equals(facing) ? 1 : "north".equals(facing) ? -1 : 0;

            double hOff = ThreadLocalRandom.current().nextDouble() * 0.6 - 0.3;
            double xOff = xAxis ? stepX * 0.52 : hOff;
            double yOff = ThreadLocalRandom.current().nextDouble() * 6.0 / 16.0;
            double zOff = xAxis ? hOff : stepZ * 0.52;

            world.spawnParticle(Particle.SMOKE, cx + xOff, cy + yOff, cz + zOff,
                    1, 0.0, 0.0, 0.0, 0.0, null, false);
            world.spawnParticle(Particle.FLAME, cx + xOff, cy + yOff, cz + zOff,
                    1, 0.0, 0.0, 0.0, 0.0, null, false);
        }

        StoveBlockEntityController ctrl = getController(block);
        if (ctrl == null || ctrl.isEmpty()) return;

        ItemStack[] items = ctrl.getItems();
        for (int i = 0; i < StoveBlockEntityController.SLOT_COUNT; i++) {
            if (items[i].isEmpty()) continue;
            if (ThreadLocalRandom.current().nextDouble() >= cfg.itemSmokeChance) continue;

            Location itemLoc = getSlotWorldPosition(block, i, cfg);
            if (itemLoc == null) continue;

            for (int k = 0; k < cfg.itemSmokeCount; ++k) {
                world.spawnParticle(Particle.SMOKE,
                        itemLoc.getX(), itemLoc.getY(), itemLoc.getZ(),
                        1, 0.0, 0.0005, 0.0, 0.0, null, false);
            }
        }
    }

    private static boolean isFrontBlocked(Block block, String facing) {
        Block front = getFrontBlock(block, facing);
        return front != null && front.getType().isOccluding();
    }

    private static Block getFrontBlock(Block block, String facing) {
        return switch (facing) {
            case "north" -> block.getRelative(BlockFace.NORTH);
            case "south" -> block.getRelative(BlockFace.SOUTH);
            case "east" -> block.getRelative(BlockFace.EAST);
            case "west" -> block.getRelative(BlockFace.WEST);
            default -> null;
        };
    }

    StoveBlockEntityController getController(Block block) {
        try {
            var ceWorld = CraftEngineUtil.getLoadedWorld(block.getWorld());
            if (ceWorld == null) return null;
            var cePos = new BlockPos(block.getX(), block.getY(), block.getZ());
            BlockEntity be = ceWorld.getBlockEntityAtIfLoaded(cePos);
            return getController(be);
        } catch (Exception e) {
            return null;
        }
    }

    private StoveBlockEntityController getController(BlockEntity be) {
        if (be == null) return null;
        try {
            var ref = new ControllerRef();
            be.controller.let(StoveBlockEntityController.class, ref::set);
            return ref.controller;
        } catch (Exception e) {
            return null;
        }
    }

    private static final class ControllerRef {
        StoveBlockEntityController controller;
        void set(StoveBlockEntityController c) { this.controller = c; }
    }

    static float getDisplayYaw(String facing) {
        return switch (facing) {
            case "east" -> 270f;
            case "south" -> 180f;
            case "west" -> 90f;
            default -> 0f;
        };
    }

    ConfigManager.SoundConfig soundPlaceFood() {
        return config.soundPlaceFood;
    }

    void playSound(Block block, ConfigManager.SoundConfig cfg) {
        if (cfg == null) return;
        dev.tako.papersdelight.config.ConfigManager.playSound(block.getWorld(),
                block.getX() + 0.5, block.getY() + 0.5, block.getZ() + 0.5,
                cfg);
    }

    public static void setAmbientSoundConfig(String soundKey,
                                              float iMin, float iMax,
                                              float vMin, float vMax,
                                              float pMin, float pMax) {
        StoveConfig.AmbientSound next = new StoveConfig.AmbientSound(
                soundKey, iMin, iMax, vMin, vMax, pMin, pMax);
        synchronized (StoveManager.class) {
            ambientSound = next;
            if (instance != null) {
                instance.config = instance.config.withAmbientSound(next);
            }
        }
    }

    private void ambientSoundTick(Block block, Location loc, StoveConfig cfg) {
        StoveConfig.AmbientSound sound = cfg.ambientSound;
        int nearby = countNearbyParticleTasks(loc);
        if (dev.tako.papersdelight.util.ParticleThrottle.shouldSkip(
                nearby, cfg.ambientSoundThrottle.threshold, cfg.ambientSoundThrottle.maxRate)) return;
        playAmbientSoundNMS(block, sound,
                randRange(sound.volumeMin(), sound.volumeMax()),
                randRange(sound.pitchMin(), sound.pitchMax()));
    }

    private void playAmbientSoundNMS(Block block, StoveConfig.AmbientSound sound, float vol, float pit) {
        Object nmsWorld = NMSHelper.nmsLevelOf(block.getWorld());
        Object nmsPos = LocationUtils.toBlockPos(block.getX(), block.getY(), block.getZ());
        if (NMSHelper.playSoundByKey(nmsWorld, nmsPos, sound.soundKey(), vol, pit)) return;

        block.getWorld().playSound(
                new org.bukkit.Location(block.getWorld(), block.getX() + 0.5, block.getY(), block.getZ() + 0.5),
                sound.soundKey(), vol, pit);
    }

    private static float randRange(float min, float max) {
        if (min >= max) return min;
        return min + ThreadLocalRandom.current().nextFloat() * (max - min);
    }
}
