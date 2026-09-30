package dev.tako.papersdelight.mechanic.skewer;

import dev.tako.papersdelight.ce.CraftEngineUtil;
import cn.chengzhimeow.ccscheduler.scheduler.CCScheduler;
import cn.chengzhimeow.ccscheduler.task.CCTask;
import dev.tako.papersdelight.api.heat.HeatSourceGate;
import cn.chengzhimeow.ccscheduler.task.CCTask;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class HandheldSkewerManager implements Listener {

    private static final CCScheduler SCHEDULER = CCScheduler.getInstance();
    static HandheldSkewerManager instance;

    final JavaPlugin plugin;
    private final NamespacedKey sessionKey;
    private final NamespacedKey sourceKey;
    private final NamespacedKey originalKey;
    private final NamespacedKey rawKey;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    private final Set<String> warnedProxyDurations = ConcurrentHashMap.newKeySet();

    public HandheldSkewerManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.sessionKey = new NamespacedKey(plugin, "handheld_skewer_session");
        this.sourceKey = new NamespacedKey(plugin, "handheld_skewer_source");
        this.originalKey = new NamespacedKey(plugin, "handheld_skewer_original");
        this.rawKey = new NamespacedKey(plugin, "handheld_skewer_raw");
    }

    public void load() {
        instance = this;
        for (Player player : Bukkit.getOnlinePlayers()) {
            SCHEDULER.getEntityScheduler().runTask(plugin, player, () -> settleOrphanedProxies(player));
        }
    }

    public void stopAll() {
        if (instance == this) instance = null;
        for (Player player : Bukkit.getOnlinePlayers()) {
            ShutdownDispatch.settle(
                    plugin.isEnabled(),
                    player,
                    this::cancel,
                    target -> SCHEDULER.getEntityScheduler().runTask(plugin, target, () -> cancel(target)));
        }
        for (Session session : sessions.values()) session.cancelTask();
        sessions.clear();
    }

    boolean tryStart(Player player, EquipmentSlot hand, HandheldSkewerBehavior.Settings settings) {
        if (player == null || hand == null || settings == null) return false;
        warnIfProxyDurationMayBeTooShort(settings);
        if (sessions.containsKey(player.getUniqueId())) return true;
        if (!nearHeatSource(player)) return false;

        ItemStack held = player.getInventory().getItem(hand);
        if (held == null || held.isEmpty()) return false;
        ItemStack proxy = CraftEngineUtil.createItem(settings.cookingProxy(), 1);
        if (proxy == null || proxy.isEmpty()) return false;

        HandheldSkewerStackState state = HandheldSkewerStackState.start(held.getAmount());
        byte[] sourceBytes = held.clone().serializeAsBytes();
        UUID id = UUID.randomUUID();
        HandheldSkewerEscrow escrow = new HandheldSkewerEscrow(id, sourceBytes, state);

        ItemMeta meta = proxy.getItemMeta();
        escrow.writeTo(meta, sessionKey, sourceKey, originalKey, rawKey);
        meta.setMaxStackSize(1);
        if (meta instanceof Damageable damageable) {
            damageable.setMaxDamage(HandheldSkewerProgressBar.BAR_MAX_DAMAGE);
            damageable.setDamage(HandheldSkewerProgressBar.BAR_MAX_DAMAGE);
        }
        proxy.setItemMeta(meta);
        proxy.setAmount(1);
        player.getInventory().setItem(hand, proxy);

        Session session = new Session(id, hand, settings, escrow,
                new HandheldSkewerProgress(settings.cookTicks()));
        sessions.put(player.getUniqueId(), session);
        session.task = SCHEDULER.getEntityScheduler().runTaskTimer(plugin, player,
                () -> tick(player, session), 1L, 1L);
        return true;
    }

    private void warnIfProxyDurationMayBeTooShort(HandheldSkewerBehavior.Settings settings) {
        String key = settings.cookingProxy() + ':' + settings.cookTicks();
        if (warnedProxyDurations.add(key)) {
            plugin.getLogger().warning("Handheld skewer proxy " + settings.cookingProxy()
                    + " must configure CE consumable consume_seconds >= "
                    + (settings.cookTicks() / 20.0D) + " (cook_ticks / 20)."
                    + " This behavior cannot read the CE item YAML to validate it.");
        }
    }

    private void tick(Player player, Session session) {
        if (sessions.get(player.getUniqueId()) != session) return;
        ItemStack held = player.getInventory().getItem(session.hand);
        if (!player.isOnline() || player.isDead()
                || !isSessionProxy(held, session.id, session.settings.cookingProxy())) {
            cancel(player);
            return;
        }

        boolean activeUsing = player.hasActiveItem()
                && player.getActiveItemHand() == session.hand
                && isSessionProxy(player.getActiveItem(), session.id, session.settings.cookingProxy());
        HandheldSkewerUseGate.Result use = session.useGate.tick(activeUsing);
        if (use == HandheldSkewerUseGate.Result.CANCEL) {
            cancel(player);
            return;
        }
        if (use == HandheldSkewerUseGate.Result.WAITING) return;

        session.progress.tick(true);
        showProgress(held, session.progress, session);
    }

    private boolean completeOne(Player player, Session session) {
        if (sessions.get(player.getUniqueId()) != session || !session.progress.isComplete()) return false;

        ItemStack proxy = player.getInventory().getItem(session.hand);
        HandheldSkewerEscrow proxyEscrow = escrowOf(proxy);
        boolean validProxy = isSessionProxy(proxy, session.id, session.settings.cookingProxy())
                && proxyEscrow != null
                && proxyEscrow.state().equals(session.escrow.state())
                && java.util.Arrays.equals(proxyEscrow.sourceBytes(), session.escrow.sourceBytes());
        ItemStack result = CraftEngineUtil.createItem(session.settings.result(), 1);
        if (!validProxy || result == null || result.isEmpty()) {
            settle(player);
            return false;
        }

        HandheldSkewerEscrow next = session.escrow.consumeOne();
        if (!writeEscrow(proxy, next)) {
            settle(player);
            return false;
        }

        session.escrow = next;
        deliverOneCooked(player, result);
        if (!session.escrow.state().hasRaw()) {
            if (sessions.remove(player.getUniqueId(), session)) session.cancelTask();
            player.getInventory().setItem(session.hand, null);
            player.clearActiveItem();
            return true;
        }

        player.getInventory().setItem(session.hand, proxy);
        session.progress.reset();
        session.useGate.reset();
        session.lastWrittenDamage = -1;
        showProgress(proxy, session.progress, session);
        return true;
    }

    private void deliverOneCooked(Player player, ItemStack result) {
        giveOrDrop(player, result);
    }

    private boolean writeEscrow(ItemStack proxy, HandheldSkewerEscrow escrow) {
        if (proxy == null || proxy.isEmpty()) return false;
        ItemMeta meta = proxy.getItemMeta();
        escrow.writeTo(meta, sessionKey, sourceKey, originalKey, rawKey);
        proxy.setItemMeta(meta);
        return true;
    }

    private boolean settle(Player player) {
        Session session = sessions.get(player.getUniqueId());
        if (session == null) {
            settleOrphanedProxies(player);
            player.clearActiveItem();
            return false;
        }
        if (!sessions.remove(player.getUniqueId(), session)) return false;
        session.cancelTask();
        restoreRawToHand(player, session);
        player.clearActiveItem();
        return true;
    }

    private void restoreRawToHand(Player player, Session session) {
        if (!session.escrow.state().hasRaw()) {
            ItemStack current = player.getInventory().getItem(session.hand);
            if (hasProxyMarkers(current)) player.getInventory().setItem(session.hand, null);
            return;
        }
        ItemStack raw = sourceOf(session.escrow.sourceBytes());
        if (raw == null) {
            clearProxyMarkers(player.getInventory().getItem(session.hand));
            return;
        }
        raw.setAmount(session.escrow.state().remainingRawAmount());
        ItemStack current = player.getInventory().getItem(session.hand);
        if (current == null || current.isEmpty() || hasProxyMarkers(current)) {
            player.getInventory().setItem(session.hand, raw);
        } else {
            giveOrDrop(player, raw);
        }
    }

    private void cancel(Player player) {
        settle(player);
    }

    private void settleOrphanedProxies(Player player) {
        Session live = sessions.get(player.getUniqueId());
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            ItemStack current = player.getInventory().getItem(slot);
            HandheldSkewerEscrow escrow = escrowOf(current);
            if (escrow != null) {
                if (live != null && live.id.equals(escrow.sessionId())) continue;
                if (!escrow.state().hasRaw()) {
                    player.getInventory().setItem(slot, null);
                    continue;
                }
                ItemStack raw = sourceOf(escrow.sourceBytes());
                if (raw != null) {
                    raw.setAmount(escrow.state().remainingRawAmount());
                    player.getInventory().setItem(slot, raw);
                } else {
                    clearProxyMarkers(current);
                }
                continue;
            }

            if (hasEscrowCounters(current)) {
                warnCorruptEscrow(player);
                player.getInventory().setItem(slot, null);
                continue;
            }
            ItemStack source = sourceOf(current);
            if (source != null) player.getInventory().setItem(slot, source);
            else if (hasProxyMarkers(current)) clearProxyMarkers(current);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onProxyConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        Session session = sessions.get(player.getUniqueId());
        ItemStack consumed = event.getItem();
        EquipmentSlot eventHand = event.getHand();
        ItemStack held = eventHand == null ? null : player.getInventory().getItem(eventHand);
        boolean carriesFeaturePdc = hasProxyMarkers(consumed) || hasProxyMarkers(held);

        HandheldSkewerConsumeValidator.Decision decision = HandheldSkewerConsumeValidator.decide(
                session == null ? null : session.id,
                session == null ? null : session.hand,
                session == null ? null : session.settings.cookingProxy(),
                session == null ? null : session.escrow.sourceBytes(),
                sessionId(consumed), eventHand, itemIdOrNull(consumed, session), sourceBytes(consumed),
                sessionId(held), itemIdOrNull(held, session), sourceBytes(held),
                session == null ? 0 : session.progress.elapsedTicks(),
                session == null ? 1 : session.progress.totalTicks(), carriesFeaturePdc);
        if (!decision.cancelsEvent()) return;

        event.setCancelled(true);
        if (decision == HandheldSkewerConsumeValidator.Decision.KEEP_SESSION) return;
        if (decision == HandheldSkewerConsumeValidator.Decision.COMPLETE) {
            completeOne(player, session);
            return;
        }

        recoverInvalidConsume(player, session, eventHand, consumed);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Session session = sessions.get(player.getUniqueId());
        if (session == null) return;

        boolean currentIsProxy = isSessionProxy(event.getCurrentItem(), session.id, session.settings.cookingProxy());
        boolean cursorIsProxy = isSessionProxy(event.getCursor(), session.id, session.settings.cookingProxy());
        boolean hotbarOrOffhandIsProxy = false;
        int hotbarButton = event.getHotbarButton();
        if (hotbarButton >= 0) {
            hotbarOrOffhandIsProxy = isSessionProxy(player.getInventory().getItem(hotbarButton),
                    session.id, session.settings.cookingProxy());
        }
        if (!hotbarOrOffhandIsProxy && event.getClick() == org.bukkit.event.inventory.ClickType.SWAP_OFFHAND) {
            hotbarOrOffhandIsProxy = isSessionProxy(player.getInventory().getItemInOffHand(),
                    session.id, session.settings.cookingProxy());
        }
        if (HandheldSkewerProxyMoveGuard.blocksClick(true, currentIsProxy, cursorIsProxy, hotbarOrOffhandIsProxy)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Session session = sessions.get(player.getUniqueId());
        if (session == null) return;

        boolean oldCursorIsProxy = isSessionProxy(event.getOldCursor(), session.id, session.settings.cookingProxy());
        boolean draggedProxy = event.getNewItems().values().stream().anyMatch(item ->
                isSessionProxy(item, session.id, session.settings.cookingProxy()));
        if (HandheldSkewerProxyMoveGuard.blocksDrag(true, oldCursorIsProxy, draggedProxy)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        Session session = sessions.get(player.getUniqueId());
        if (session == null) return;

        boolean mainHandIsProxy = isSessionProxy(event.getMainHandItem(), session.id, session.settings.cookingProxy());
        boolean offHandIsProxy = isSessionProxy(event.getOffHandItem(), session.id, session.settings.cookingProxy());
        if (HandheldSkewerProxyMoveGuard.blocksSwap(true, mainHandIsProxy, offHandIsProxy)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        ItemStack dropped = event.getItemDrop().getItemStack();
        UUID droppedSessionId = sessionId(dropped);
        HandheldSkewerEscrow escrow = escrowOf(dropped);
        if (escrow == null) {
            if (hasEscrowCounters(dropped)) {
                warnCorruptEscrow(player);
                event.getItemDrop().remove();
                finishDroppedSession(player, droppedSessionId);
                return;
            }
            ItemStack legacy = sourceOf(dropped);
            if (legacy != null) event.getItemDrop().setItemStack(legacy);
            return;
        }

        ItemStack restored = restoreDroppedProxy(dropped, escrow);
        if (restored == null) event.getItemDrop().remove();
        else event.getItemDrop().setItemStack(restored);
        finishDroppedSession(player, escrow.sessionId());
    }

    private void finishDroppedSession(Player player, UUID droppedSessionId) {
        Session session = sessions.get(player.getUniqueId());
        if (session != null && session.id.equals(droppedSessionId)
                && sessions.remove(player.getUniqueId(), session)) {
            session.cancelTask();
        }
        settleOrphanedProxies(player);
        player.clearActiveItem();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        settle(event.getPlayer());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        settleOrphanedProxies(event.getPlayer());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (event.getKeepInventory()) {
            settle(player);
            return;
        }

        Session session = sessions.remove(player.getUniqueId());
        if (session != null) session.cancelTask();
        boolean restoredSession = false;
        for (int index = event.getDrops().size() - 1; index >= 0; index--) {
            ItemStack drop = event.getDrops().get(index);
            UUID dropSessionId = sessionId(drop);
            if (session != null && session.id.equals(dropSessionId)) {
                if (!session.escrow.state().hasRaw()) {
                    event.getDrops().remove(index);
                    restoredSession = true;
                    continue;
                }
                ItemStack raw = sourceOf(session.escrow.sourceBytes());
                if (raw != null) {
                    raw.setAmount(session.escrow.state().remainingRawAmount());
                    event.getDrops().set(index, raw);
                    restoredSession = true;
                    continue;
                }
            }
            ItemStack restored = restoreDeathDrop(player, drop);
            if (restored == null) event.getDrops().remove(index);
            else event.getDrops().set(index, restored);
        }
        if (session != null && !restoredSession && session.escrow.state().hasRaw()) {
            ItemStack raw = sourceOf(session.escrow.sourceBytes());
            if (raw != null) {
                raw.setAmount(session.escrow.state().remainingRawAmount());
                event.getDrops().add(raw);
            }
        }
        player.clearActiveItem();
    }

    private ItemStack restoreDroppedProxy(ItemStack dropped, HandheldSkewerEscrow escrow) {
        if (!escrow.state().hasRaw()) return null;
        ItemStack raw = sourceOf(escrow.sourceBytes());
        if (raw == null) {
            clearProxyMarkers(dropped);
            return dropped;
        }
        raw.setAmount(escrow.state().remainingRawAmount());
        return raw;
    }

    private ItemStack restoreDeathDrop(Player player, ItemStack drop) {
        HandheldSkewerEscrow escrow = escrowOf(drop);
        if (escrow != null) return restoreDroppedProxy(drop, escrow);
        if (hasEscrowCounters(drop)) {
            warnCorruptEscrow(player);
            return null;
        }
        ItemStack legacy = sourceOf(drop);
        return legacy == null ? drop : legacy;
    }

    private void warnCorruptEscrow(Player player) {
        plugin.getLogger().warning("Discarded corrupt handheld skewer escrow for player "
                + player.getUniqueId());
    }

    private ItemStack sourceOf(ItemStack stack) {
        return sourceOf(sourceBytes(stack));
    }

    private ItemStack sourceOf(byte[] encoded) {
        if (encoded == null) return null;
        try {
            ItemStack source = ItemStack.deserializeBytes(encoded);
            return source.isEmpty() ? null : source;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private HandheldSkewerEscrow escrowOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        PersistentDataContainer data = stack.getItemMeta().getPersistentDataContainer();
        return HandheldSkewerEscrow.decode(sessionId(stack), sourceBytes(stack),
                data.get(rawKey, PersistentDataType.INTEGER),
                data.get(originalKey, PersistentDataType.INTEGER));
    }

    private byte[] sourceBytes(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        return stack.getItemMeta().getPersistentDataContainer().get(sourceKey, PersistentDataType.BYTE_ARRAY);
    }

    private UUID sessionId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        String raw = stack.getItemMeta().getPersistentDataContainer().get(sessionKey, PersistentDataType.STRING);
        if (raw == null) return null;
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private String itemIdOrNull(ItemStack stack, Session session) {
        return session != null && CraftEngineUtil.isItem(stack, session.settings.cookingProxy())
                ? session.settings.cookingProxy() : null;
    }

    private void recoverInvalidConsume(Player player, Session session, EquipmentSlot hand, ItemStack consumed) {
        if (session != null) {
            EquipmentSlot sessionHand = session.hand;
            settle(player);
            if (hand == sessionHand) return;
        }
        if (hand == null) {
            clearProxyMarkers(consumed);
            return;
        }
        ItemStack current = player.getInventory().getItem(hand);
        ItemStack source = rawOf(current);
        if (source == null) source = rawOf(consumed);
        if (source != null) {
            player.getInventory().setItem(hand, source);
        } else {

            clearProxyMarkers(current);
            clearProxyMarkers(consumed);
        }
    }

    private ItemStack rawOf(ItemStack stack) {
        HandheldSkewerEscrow escrow = escrowOf(stack);
        if (escrow == null) return hasEscrowCounters(stack) ? null : sourceOf(stack);
        if (!escrow.state().hasRaw()) return null;
        ItemStack raw = sourceOf(escrow.sourceBytes());
        if (raw != null) raw.setAmount(escrow.state().remainingRawAmount());
        return raw;
    }

    private boolean hasEscrowCounters(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        PersistentDataContainer data = stack.getItemMeta().getPersistentDataContainer();
        return data.has(originalKey, PersistentDataType.INTEGER)
                || data.has(rawKey, PersistentDataType.INTEGER);
    }

    private boolean hasProxyMarkers(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        PersistentDataContainer data = stack.getItemMeta().getPersistentDataContainer();
        return data.has(sessionKey, PersistentDataType.STRING)
                || data.has(sourceKey, PersistentDataType.BYTE_ARRAY)
                || data.has(originalKey, PersistentDataType.INTEGER)
                || data.has(rawKey, PersistentDataType.INTEGER);
    }

    private void clearProxyMarkers(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        ItemMeta meta = stack.getItemMeta();
        PersistentDataContainer data = meta.getPersistentDataContainer();
        data.remove(sessionKey);
        data.remove(sourceKey);
        data.remove(originalKey);
        data.remove(rawKey);
        stack.setItemMeta(meta);
    }

    private boolean isSessionProxy(ItemStack stack, UUID id, String proxyId) {
        return id.equals(sessionId(stack)) && CraftEngineUtil.isItem(stack, proxyId);
    }

    private static boolean nearHeatSource(Player player) {
        Block origin = player.getLocation().getBlock();
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    if (HeatSourceGate.isActiveHeatSource(origin.getRelative(x, y, z))) return true;
                }
            }
        }
        return false;
    }

    private static void giveOrDrop(Player player, ItemStack stack) {
        for (ItemStack overflow : player.getInventory().addItem(stack).values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), overflow);
        }
    }

    private static void showProgress(ItemStack stack, HandheldSkewerProgress progress, Session session) {
        if (stack == null || stack.isEmpty()) return;
        ItemMeta meta = stack.getItemMeta();
        if (!(meta instanceof Damageable damageable)) return;
        int max = HandheldSkewerProgressBar.BAR_MAX_DAMAGE;
        int damage = HandheldSkewerProgressBar.damageFor(max, progress.elapsedTicks(), progress.totalTicks());
        if (damage == session.lastWrittenDamage) return;
        damageable.setMaxDamage(max);
        damageable.setDamage(damage);
        stack.setItemMeta(meta);
        session.lastWrittenDamage = damage;
    }

    private static final class Session {
        private final UUID id;
        private final EquipmentSlot hand;
        private final HandheldSkewerBehavior.Settings settings;
        private HandheldSkewerEscrow escrow;
        private final HandheldSkewerProgress progress;
        private final HandheldSkewerUseGate useGate = new HandheldSkewerUseGate();
        private int lastWrittenDamage = -1;
        private CCTask task;

        private Session(UUID id, EquipmentSlot hand, HandheldSkewerBehavior.Settings settings,
                        HandheldSkewerEscrow escrow, HandheldSkewerProgress progress) {
            this.id = id;
            this.hand = hand;
            this.settings = settings;
            this.escrow = escrow;
            this.progress = progress;
        }

        private void cancelTask() {
            if (task != null) task.cancel();
        }
    }
}
