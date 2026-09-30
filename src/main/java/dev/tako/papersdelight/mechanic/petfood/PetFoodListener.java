package dev.tako.papersdelight.mechanic.petfood;

import dev.tako.papersdelight.config.ConfigManager;
import net.momirealms.craftengine.bukkit.api.CraftEngineItems;
import net.momirealms.craftengine.core.util.Key;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class PetFoodListener implements Listener {

    private static final PotionEffect[] DOG_FOOD_EFFECTS = {
            new PotionEffect(PotionEffectType.SPEED,      6000, 0),
            new PotionEffect(PotionEffectType.STRENGTH,   6000, 0),
            new PotionEffect(PotionEffectType.RESISTANCE, 6000, 0),
    };

    private static final PotionEffect[] HORSE_FEED_EFFECTS = {
            new PotionEffect(PotionEffectType.SPEED,      6000, 1),
            new PotionEffect(PotionEffectType.JUMP_BOOST, 6000, 0),
    };

    private static Set<Key> dogFoodItems  = Collections.singleton(Key.of("farmersdelight:dog_food"));
    private static Set<Key> horseFeedItems = Collections.singleton(Key.of("farmersdelight:horse_feed"));

    public static void reload() {
        dogFoodItems   = loadItemIds("pet_food.dog_food.items",   List.of("farmersdelight:dog_food"));
        horseFeedItems = loadItemIds("pet_food.horse_feed.items", List.of("farmersdelight:horse_feed"));
    }

    private static Set<Key> loadItemIds(String configPath, List<String> defaults) {
        List<String> raw = ConfigManager.getConfig().getStringList(configPath);
        if (raw == null || raw.isEmpty()) {
            raw = defaults;
        }
        return raw.stream().map(Key::of).collect(Collectors.toCollection(HashSet::new));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (event.isCancelled()) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.isEmpty()) return;

        Key customId = CraftEngineItems.getCustomItemId(item);
        if (customId == null) return;

        if (dogFoodItems.contains(customId)) {
            handleDogFood(event, player, item);
        } else if (horseFeedItems.contains(customId)) {
            handleHorseFeed(event, player, item);
        }
    }

    private void handleDogFood(PlayerInteractEntityEvent event, Player player, ItemStack item) {
        if (!(event.getRightClicked() instanceof Wolf wolf)) return;
        if (!wolf.isTamed()) return;
        if (!wolf.isValid() || wolf.isDead()) return;

        event.setCancelled(true);
        player.swingMainHand();

        healToMax(wolf);
        for (PotionEffect effect : DOG_FOOD_EFFECTS) {
            wolf.addPotionEffect(effect);
        }

        spawnHappyParticles(wolf);
        wolf.getWorld().playSound(wolf.getLocation(), Sound.ENTITY_GENERIC_EAT, 1.0f, 1.0f);

        consumeItem(player, item, true);
    }

    private void handleHorseFeed(PlayerInteractEntityEvent event, Player player, ItemStack item) {
        Entity entity = event.getRightClicked();
        if (!isValidHorseFeedTarget(entity)) return;

        if (!(entity instanceof LivingEntity living)) return;
        if (!living.isValid() || living.isDead()) return;

        event.setCancelled(true);
        player.swingMainHand();

        healToMax(living);
        for (PotionEffect effect : HORSE_FEED_EFFECTS) {
            living.addPotionEffect(effect);
        }

        spawnHappyParticles(living);
        living.getWorld().playSound(living.getLocation(), Sound.ENTITY_HORSE_EAT, 1.0f, 1.0f);

        consumeItem(player, item, false);
    }

    private boolean isValidHorseFeedTarget(Entity entity) {
        if (entity instanceof AbstractHorse horse) return horse.isTamed();
        if (entity instanceof Llama llama)      return llama.isTamed();
        return entity instanceof Camel || entity instanceof TraderLlama;
    }

    private void spawnHappyParticles(LivingEntity entity) {
        entity.getWorld().spawnParticle(Particle.HAPPY_VILLAGER,
                entity.getLocation().add(0, entity.getHeight() / 2, 0),
                5, 0.3, 0.3, 0.3, 0);
    }

    private void healToMax(LivingEntity entity) {
        AttributeInstance maxHealth = entity.getAttribute(MAX_HEALTH_ATTR);
        if (maxHealth == null) return;
        entity.setHealth(maxHealth.getValue());
    }

    private void consumeItem(Player player, ItemStack item, boolean returnBowl) {
        if (player.getGameMode() == GameMode.CREATIVE) return;

        item.setAmount(item.getAmount() - 1);
        PlayerInventory inv = player.getInventory();
        if (item.getAmount() <= 0) {
            inv.setItemInMainHand(ItemStack.empty());
        }

        if (returnBowl) {
            ItemStack bowl = new ItemStack(Material.BOWL);
            if (inv.getItemInMainHand().isEmpty()) {
                inv.setItemInMainHand(bowl);
            } else {
                java.util.Map<Integer, ItemStack> leftover = inv.addItem(bowl);
                for (ItemStack left : leftover.values()) {
                    player.getWorld().dropItem(player.getLocation(), left);
                }
            }
        }
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
