package dev.tako.papersdelight.mechanic.skillet;

import dev.tako.papersdelight.recipe.CampfireRecipeUtil;
import dev.tako.papersdelight.ce.CraftEngineUtil;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.block.entity.BlockEntity;
import net.momirealms.craftengine.core.block.entity.BlockEntityController;
import net.momirealms.craftengine.core.block.entity.tick.BlockEntityTicker;
import net.momirealms.craftengine.core.world.BlockPos;
import net.momirealms.craftengine.core.world.CEWorld;
import net.momirealms.craftengine.core.world.chunk.CEChunk;
import net.momirealms.craftengine.libraries.nbt.CompoundTag;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.CampfireRecipe;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;
import java.util.UUID;

public final class SkilletBlockEntityController extends BlockEntityController {

    private ItemStack storedStack = ItemStack.empty();
    private int cookingTime;
    private int cookingTimeTotal;

    @Nullable
    private UUID placerUuid;

    private ItemStack skilletStack = ItemStack.empty();

    private int fireAspectLevel;
    int particleTicks;
    int lastPassTick;
    private boolean heated;
    private int heatTicks;

    public boolean heated() { return heated; }

    public void heated(boolean value) { heated = value; }

    public int heatTicks() { return heatTicks; }

    public void heatTicks(int value) { heatTicks = value; }

    public SkilletBlockEntityController(BlockEntity blockEntity) {
        super(blockEntity);
    }

    @Override
    public <C extends BlockEntityController> BlockEntityTicker<C> createBlockEntityTicker(CEWorld world, ImmutableBlockState blockState) {
        return createTickerHelper(SkilletBlockEntityController::tick);
    }

    public static void tick(CEWorld world, BlockPos pos, ImmutableBlockState state, SkilletBlockEntityController controller) {
        SkilletManager manager = SkilletManager.instance;
        if (manager != null) manager.tickSkillet(controller, world, pos);
    }

    @Override
    public void onUnload() {
        SkilletManager manager = SkilletManager.instance;
        if (manager != null) manager.forgetSkillet(this);
    }

    public ItemStack getStoredStack() {
        return storedStack.clone();
    }

    public boolean hasStoredStack() {
        return !storedStack.isEmpty();
    }

    public boolean isEmpty() {
        return storedStack.isEmpty();
    }

    public ItemStack addItemToCook(ItemStack addedStack) {
        return addItemToCook(addedStack, null);
    }

    public ItemStack addItemToCook(ItemStack addedStack, @Nullable UUID placer) {
        if (addedStack == null || addedStack.isEmpty()) return ItemStack.empty();
        if (!storedStack.isEmpty()) return addedStack.clone();

        CampfireRecipe recipe = CampfireRecipeUtil.findRecipe(addedStack);
        if (recipe == null) return addedStack.clone();

        int toInsert = Math.min(addedStack.getAmount(), 64);
        storedStack = addedStack.clone();
        storedStack.setAmount(toInsert);
        cookingTime = 0;
        cookingTimeTotal = SkilletCookingTime.calculate(recipe.getCookingTime(), fireAspectLevel);
        placerUuid = placer;
        markUnsaved();

        int remaining = addedStack.getAmount() - toInsert;
        if (remaining <= 0) return ItemStack.empty();
        ItemStack remainder = addedStack.clone();
        remainder.setAmount(remaining);
        return remainder;
    }

    @Nullable
    public UUID getPlacerUuid() {
        return placerUuid;
    }

    public ItemStack removeItem() {
        if (storedStack.isEmpty()) return ItemStack.empty();
        ItemStack result = storedStack.clone();
        storedStack = ItemStack.empty();
        cookingTime = 0;
        cookingTimeTotal = 0;
        placerUuid = null;
        markUnsaved();
        return result;
    }

    public ItemStack takeItem() {
        return removeItem();
    }

    public void setSkilletItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        skilletStack = stack.clone();
        fireAspectLevel = stack.getEnchantmentLevel(Enchantment.FIRE_ASPECT);
        markUnsaved();
    }

    public ItemStack getSkilletAsItem() {
        if (skilletStack.isEmpty()) {
            return CraftEngineUtil.createItem("farmersdelight:skillet", 1);
        }
        return skilletStack.clone();
    }

    public int getFireAspectLevel() {
        return fireAspectLevel;
    }

    public ItemStack serverTick(boolean heated, boolean waterlogged) {
        if (storedStack.isEmpty()) return null;

        if (waterlogged) {
            ItemStack ejected = storedStack.clone();
            storedStack = ItemStack.empty();
            cookingTime = 0;
            cookingTimeTotal = 0;
            placerUuid = null;
            markUnsaved();
            return ejected;
        }

        if (heated) {
            return cookAndOutput();
        } else {
            coolDown();
            return null;
        }
    }

    private ItemStack cookAndOutput() {
        if (cookingTimeTotal <= 0) {
            CampfireRecipe recipe = CampfireRecipeUtil.findRecipe(storedStack);
            if (recipe == null) {
                ItemStack ejected = storedStack.clone();
                storedStack = ItemStack.empty();
                cookingTime = 0;
                cookingTimeTotal = 0;
                placerUuid = null;
                markUnsaved();
                return ejected;
            }
            cookingTimeTotal = SkilletCookingTime.calculate(recipe.getCookingTime(), fireAspectLevel);
        }

        cookingTime++;
        if (cookingTime >= cookingTimeTotal) {
            ItemStack result = CampfireRecipeUtil.getResult(storedStack);
            if (result != null && !result.isEmpty()) {
                result = result.clone();
            }
            storedStack.setAmount(storedStack.getAmount() - 1);
            if (storedStack.getAmount() <= 0) {
                storedStack = ItemStack.empty();
                placerUuid = null;
            }
            cookingTime = 0;
            cookingTimeTotal = 0;
            markUnsaved();
            return result;
        }
        return null;
    }

    private void coolDown() {
        if (cookingTime <= 0) return;
        cookingTime = Math.max(0, cookingTime - 2);
    }

    void markUnsaved() {
        CEWorld ceWorld = this.blockEntity.world();
        if (ceWorld == null) return;
        CEChunk chunk = ceWorld.getChunkAtIfLoaded(this.blockEntity.pos());
        if (chunk != null) {
            chunk.setUnsaved(true);
        }
    }

    @Override
    public void saveCustomData(CompoundTag tag) {
        if (!storedStack.isEmpty()) {
            try { tag.putByteArray("item_bytes", storedStack.serializeAsBytes()); } catch (Exception ignored) {}
            String id = CraftEngineUtil.getItemIdentifier(storedStack);
            if (id != null) {
                tag.putString("item_id", id);
                tag.putInt("item_count", storedStack.getAmount());
            }
        }
        tag.putInt("cook_time", cookingTime);
        tag.putInt("cook_time_total", cookingTimeTotal);
        if (placerUuid != null) {
            tag.putString("placer_uuid", placerUuid.toString());
        }
        if (!skilletStack.isEmpty()) {
            try { tag.putByteArray("skillet_bytes", skilletStack.serializeAsBytes()); } catch (Exception ignored) {}
        }
        tag.putInt("fire_aspect", fireAspectLevel);
    }

    @Override
    public void loadCustomData(CompoundTag tag) {
        storedStack = ItemStack.empty();
        byte[] bytes = tag.getByteArray("item_bytes");
        if (bytes != null && bytes.length > 0) {
            try { storedStack = ItemStack.deserializeBytes(bytes); } catch (Exception ignored) {}
        }
        if (storedStack.isEmpty()) {
            String id = tag.getString("item_id");
            if (id != null && !id.isEmpty()) {
                int count = Math.max(1, tag.getInt("item_count"));
                ItemStack created = CraftEngineUtil.createItem(id, count);
                storedStack = (created != null) ? created : ItemStack.empty();
            }
        }
        cookingTime = tag.getInt("cook_time");
        cookingTimeTotal = tag.getInt("cook_time_total");

        String uuidStr = tag.getString("placer_uuid");
        if (uuidStr != null && !uuidStr.isEmpty()) {
            try {
                placerUuid = UUID.fromString(uuidStr);
            } catch (IllegalArgumentException ignored) {
                placerUuid = null;
            }
        } else {
            placerUuid = null;
        }

        skilletStack = ItemStack.empty();
        byte[] sb = tag.getByteArray("skillet_bytes");
        if (sb != null && sb.length > 0) {
            try { skilletStack = ItemStack.deserializeBytes(sb); } catch (Exception ignored) {}
        }
        fireAspectLevel = tag.getInt("fire_aspect");
        if (fireAspectLevel == 0 && !skilletStack.isEmpty()) {

            fireAspectLevel = skilletStack.getEnchantmentLevel(Enchantment.FIRE_ASPECT);
        }
    }
}
