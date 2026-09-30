package dev.tako.papersdelight.util;

import dev.tako.papersdelight.config.ConfigManager;
import net.momirealms.craftengine.libraries.nbt.CompoundTag;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.UUID;

public final class CraftEngineSerializationWarmup {

    private static final String CRAFTENGINE_NETWORK_PROXY_CLASS =
            "net.momirealms.craftengine.proxy.minecraft.network.RegistryFriendlyByteBufProxy";

    private static final String[] PRELOAD_CLASSES = {
            "net.momirealms.craftengine.libraries.nbt.ByteArrayTag",
            "net.momirealms.craftengine.libraries.nbt.ByteTag",
            "net.momirealms.craftengine.libraries.nbt.CompoundTag",
            "net.momirealms.craftengine.libraries.nbt.DoubleTag",
            "net.momirealms.craftengine.libraries.nbt.FloatTag",
            "net.momirealms.craftengine.libraries.nbt.IntArrayTag",
            "net.momirealms.craftengine.libraries.nbt.IntTag",
            "net.momirealms.craftengine.libraries.nbt.ListTag",
            "net.momirealms.craftengine.libraries.nbt.LongArrayTag",
            "net.momirealms.craftengine.libraries.nbt.LongTag",
            "net.momirealms.craftengine.libraries.nbt.ShortTag",
            "net.momirealms.craftengine.libraries.nbt.StringTag",
            "dev.tako.papersdelight.cookingpot.CookingPotBlockEntityController",
            "dev.tako.papersdelight.mechanic.cutting.CuttingBoardBlockEntityController",
            "dev.tako.papersdelight.mechanic.skillet.SkilletBlockEntityController",
            "dev.tako.papersdelight.mechanic.stove.StoveBlockEntityController"
    };

    private CraftEngineSerializationWarmup() {
    }

    public static void warmNetworkProxy(Plugin plugin) {
        preload(plugin, CRAFTENGINE_NETWORK_PROXY_CLASS, craftEngineClassLoader(plugin));
    }

    public static void run(Plugin plugin) {
        ClassLoader loader = CraftEngineSerializationWarmup.class.getClassLoader();
        for (String className : PRELOAD_CLASSES) {
            preload(plugin, className, loader);
        }

        try {
            CompoundTag tag = new CompoundTag();
            tag.putByte("byte", (byte) 1);
            tag.putBoolean("boolean", true);
            tag.putShort("short", (short) 1);
            tag.putInt("int", 1);
            tag.putLong("long", 1L);
            tag.putFloat("float", 1.0F);
            tag.putDouble("double", 1.0D);
            tag.putString("string", "warmup");
            tag.putByteArray("bytes", new byte[]{1});
            tag.putIntArray("ints", new int[]{1});
            tag.putLongArray("longs", new long[]{1L});
            tag.putUUID("uuid", new UUID(0L, 0L));
            tag.getByteArray("bytes");
            tag.getInt("int");
            tag.getIntArray("ints");
            tag.getFloat("float");
            tag.getString("string");
        } catch (Throwable throwable) {
            plugin.getLogger().fine(ConfigManager.getOr("warm_nbt", "Unable to warm CraftEngine NBT serialization: %error%").replace("%error%", ConfigManager.describeError(throwable)));
        }

        try {
            byte[] bytes = new ItemStack(Material.STONE).serializeAsBytes();
            ItemStack.deserializeBytes(bytes);
        } catch (Throwable throwable) {
            plugin.getLogger().fine(ConfigManager.getOr("warm_bukkit", "Unable to warm Bukkit item serialization: %error%").replace("%error%", ConfigManager.describeError(throwable)));
        }
    }

    private static ClassLoader craftEngineClassLoader(Plugin plugin) {
        Plugin craftEngine = plugin.getServer().getPluginManager().getPlugin("CraftEngine");
        if (craftEngine != null) {
            return craftEngine.getClass().getClassLoader();
        }
        return CraftEngineSerializationWarmup.class.getClassLoader();
    }

    private static void preload(Plugin plugin, String className, ClassLoader loader) {
        try {
            Class.forName(className, true, loader);
        } catch (Throwable throwable) {
            plugin.getLogger().fine(ConfigManager.getOr("warm_preload", "Unable to preload serialization class %class%: %error%").replace("%class%", className).replace("%error%", ConfigManager.describeError(throwable)));
        }
    }
}
