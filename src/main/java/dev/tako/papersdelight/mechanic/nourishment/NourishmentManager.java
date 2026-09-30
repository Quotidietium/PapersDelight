package dev.tako.papersdelight.mechanic.nourishment;

import cn.chengzhimeow.ccscheduler.scheduler.CCScheduler;
import dev.tako.papersdelight.config.ConfigManager;
import dev.tako.papersdelight.effect.TimedEffectManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class NourishmentManager extends TimedEffectManager {

    private static final CCScheduler SCHEDULER = CCScheduler.getInstance();

    public static final String EFFECT_ID = "nourishment";

    public static final String QUALIFIED_ID = "papersdelight:nourishment_effect";

    public static final String NAME_KEY = "effect.farmersdelight.nourishment";

    private static NourishmentManager INSTANCE;

    private boolean alwaysEatEnabled = true;

    private final Set<UUID> fakedHunger = ConcurrentHashMap.newKeySet();

    public NourishmentManager(JavaPlugin plugin) {
        super(plugin, EFFECT_ID, QUALIFIED_ID, NAME_KEY);
        INSTANCE = this;
    }

    public static NourishmentManager getInstance() {
        return INSTANCE;
    }

    public void load() {
        boolean enabled = ConfigManager.getConfigBoolean("nourishment_effect.enable", true);
        String color = ConfigManager.getConfigString("nourishment_effect.bossbar.color", "YELLOW");
        String style = ConfigManager.getConfigString("nourishment_effect.bossbar.style", "SEGMENTED_20");
        alwaysEatEnabled = ConfigManager.getConfigBoolean("nourishment_effect.always_eat", true);
        configure(enabled, color, style);
    }

    public void applyNourishment(Player player, int durationTicks) {
        applyEffect(player, durationTicks);
    }

    public void removeNourishment(Player player) {
        removeEffect(player);
    }

    @Override
    protected void onApply(Player player, int durationTicks, int amplifier) {
        dev.tako.papersdelight.stats.StatsManager stats =
                dev.tako.papersdelight.stats.StatsManager.getInstance();
        if (stats != null) stats.recordEffectApply(player, EFFECT_ID);
    }

    @Override
    protected void onEffectTick(Player player, int amplifier, int now) {
        applyExhaustionFreeze(player);
        applyAlwaysEat(player);
    }

    private void applyExhaustionFreeze(Player player) {
        if (player.getGameMode() == org.bukkit.GameMode.CREATIVE
                || player.getGameMode() == org.bukkit.GameMode.SPECTATOR) return;

        AttributeInstance maxHealth = player.getAttribute(MAX_HEALTH_ATTR);
        double maxHealthValue = maxHealth == null ? player.getHealth() : maxHealth.getValue();

        boolean healingWithSaturation = Boolean.TRUE.equals(
                        player.getWorld().getGameRuleValue(org.bukkit.GameRule.NATURAL_REGENERATION))
                && player.getHealth() < maxHealthValue
                && player.getSaturation() > 0f;

        if (!healingWithSaturation) {
            player.setExhaustion(0f);
        }
    }

    private void applyAlwaysEat(Player player) {
        if (!alwaysEatEnabled) return;

        if (player.getGameMode() == org.bukkit.GameMode.CREATIVE
                || player.getGameMode() == org.bukkit.GameMode.SPECTATOR) return;

        UUID id = player.getUniqueId();
        boolean alreadyFaked = fakedHunger.contains(id);
        boolean holdingFood = isHoldingFood(player);

        if (NourishmentHungerMath.shouldFakeHunger(holdingFood, player.getFoodLevel(), alreadyFaked)) {
            player.setFoodLevel(NourishmentHungerMath.FAKED_FOOD_LEVEL);
            fakedHunger.add(id);
        } else if (NourishmentHungerMath.shouldRestoreHunger(holdingFood, alreadyFaked)) {
            restoreFakedHunger(player);
        }
    }

    private static boolean isHoldingFood(Player player) {
        return isFood(player.getInventory().getItemInMainHand())
                || isFood(player.getInventory().getItemInOffHand());
    }

    private static boolean isFood(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        ItemMeta meta = stack.getItemMeta();
        if (meta != null && meta.hasFood()) return true;
        return stack.getType().isEdible();
    }

    private boolean restoreFakedHunger(Player player) {
        if (!fakedHunger.remove(player.getUniqueId())) return false;
        if (player.getFoodLevel() < NourishmentHungerMath.FULL_FOOD_LEVEL) {
            player.setFoodLevel(NourishmentHungerMath.FULL_FOOD_LEVEL);
        }
        return true;
    }

    @EventHandler(ignoreCancelled = true)
    public void onMilkBucketConsume(PlayerItemConsumeEvent event) {
        if (event.getItem().getType() != Material.MILK_BUCKET) return;
        Player player = event.getPlayer();
        for (TimedEffectManager manager : TimedEffectManager.registered()) {
            manager.removeEffect(player);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFoodConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        if (!fakedHunger.contains(player.getUniqueId())) return;
        SCHEDULER.getEntityScheduler().runTaskLater(plugin, player, st -> {
            if (!player.isOnline()) return;
            restoreFakedHunger(player);
        }, 1L);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuitRestoreHunger(PlayerQuitEvent event) {
        restoreFakedHunger(event.getPlayer());
    }

    @Override
    protected void onExpire(Player player) {
        restoreFakedHunger(player);
    }

    @Override
    protected void onRemove(Player player, RemovalCause cause) {
        restoreFakedHunger(player);
    }

    @Override
    public void stopAll() {
        for (UUID id : fakedHunger) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && player.isOnline()) {
                restoreFakedHunger(player);
            }
        }
        fakedHunger.clear();
        super.stopAll();
    }

    public static String formatDuration(int ticks) {
        return TimedEffectManager.formatDuration(ticks);
    }

    @SuppressWarnings("deprecation")
    private static final Attribute MAX_HEALTH_ATTR = resolveMaxHealth();

    @SuppressWarnings("deprecation")
    private static Attribute resolveMaxHealth() {
        try {
            return (Attribute) Attribute.class.getField("MAX_HEALTH").get(null);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            return Attribute.GENERIC_MAX_HEALTH;
        }
    }
}
