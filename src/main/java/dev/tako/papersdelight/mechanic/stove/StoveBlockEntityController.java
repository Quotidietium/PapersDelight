package dev.tako.papersdelight.mechanic.stove;

import dev.tako.papersdelight.config.ConfigManager;
import dev.tako.papersdelight.recipe.CampfireRecipeUtil;
import dev.tako.papersdelight.ce.CraftEngineUtil;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.block.entity.BlockEntity;
import net.momirealms.craftengine.core.block.entity.BlockEntityController;
import net.momirealms.craftengine.core.block.entity.tick.BlockEntityTicker;
import net.momirealms.craftengine.core.world.BlockPos;
import net.momirealms.craftengine.core.world.CEWorld;
import net.momirealms.craftengine.libraries.nbt.CompoundTag;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class StoveBlockEntityController extends BlockEntityController {

    static final int SLOT_COUNT = 6;

    private final ItemStack[] slots = new ItemStack[SLOT_COUNT];

    private final int[] cookingProgress = new int[SLOT_COUNT];

    private final int[] cookingTotalTime = new int[SLOT_COUNT];
    long particleCountdown;
    long ambientCountdown;
    int lastPassTick;

    public StoveBlockEntityController(BlockEntity blockEntity) {
        super(blockEntity);
        for (int i = 0; i < SLOT_COUNT; i++) {
            slots[i] = ItemStack.empty();
        }
    }

    @Override
    public <C extends BlockEntityController> BlockEntityTicker<C> createBlockEntityTicker(CEWorld world, ImmutableBlockState blockState) {
        return createTickerHelper(StoveBlockEntityController::tick);
    }

    public static void tick(CEWorld world, BlockPos pos, ImmutableBlockState state, StoveBlockEntityController controller) {
        StoveManager manager = StoveManager.instance;
        if (manager != null) manager.tickStove(controller, world, pos);
    }

    @Override
    public void onUnload() {
        StoveManager manager = StoveManager.instance;
        if (manager != null) manager.forgetStove(this);
    }

    public ItemStack getSlotItem(int slot) {
        return (slot >= 0 && slot < SLOT_COUNT) ? slots[slot].clone() : ItemStack.empty();
    }

    public void setSlotItem(int slot, ItemStack item) {
        if (slot < 0 || slot >= SLOT_COUNT) return;
        if (item == null || item.isEmpty()) {
            slots[slot] = ItemStack.empty();
            cookingProgress[slot] = 0;
            cookingTotalTime[slot] = 0;
        } else {
            ItemStack copy = item.clone();
            copy.setAmount(1);
            slots[slot] = copy;
            cookingProgress[slot] = 0;
            cookingTotalTime[slot] = getCampfireCookingTime(copy);
        }
    }

    public int getNextEmptySlot() {
        for (int i = 0; i < SLOT_COUNT; i++) {
            if (slots[i].isEmpty()) return i;
        }
        return -1;
    }

    public boolean placeItem(ItemStack item) {
        int slot = getNextEmptySlot();
        if (slot < 0) return false;
        setSlotItem(slot, item);
        return true;
    }

    public ItemStack takeItem(int slot) {
        if (slot < 0 || slot >= SLOT_COUNT || slots[slot].isEmpty()) return ItemStack.empty();
        ItemStack result = slots[slot].clone();
        slots[slot] = ItemStack.empty();
        cookingProgress[slot] = 0;
        cookingTotalTime[slot] = 0;
        return result;
    }

    public boolean isEmpty() {
        for (ItemStack s : slots) { if (!s.isEmpty()) return false; }
        return true;
    }

    public boolean isFull() {
        for (ItemStack s : slots) { if (s.isEmpty()) return false; }
        return true;
    }

    public ItemStack[] getItems() { return slots.clone(); }

    public List<CompletedSlot> serverTick(boolean lit) {
        if (isEmpty()) return List.of();
        if (lit) {
            return cookAndOutput();
        } else {
            coolItems();
            return List.of();
        }
    }

    private List<CompletedSlot> cookAndOutput() {
        List<CompletedSlot> completed = null;
        for (int i = 0; i < SLOT_COUNT; i++) {
            if (slots[i].isEmpty()) continue;
            if (cookingTotalTime[i] <= 0) {
                cookingTotalTime[i] = getCampfireCookingTime(slots[i]);
            }
            cookingProgress[i]++;
            if (cookingProgress[i] >= cookingTotalTime[i]) {
                ItemStack result = getCampfireResult(slots[i]);
                if (result != null && !result.isEmpty()) {
                    if (completed == null) completed = new ArrayList<>();
                    completed.add(new CompletedSlot(i, result.clone()));
                }
                slots[i] = ItemStack.empty();
                cookingProgress[i] = 0;
                cookingTotalTime[i] = 0;
            }
        }
        return completed == null ? List.of() : completed;
    }

    private void coolItems() {
        for (int i = 0; i < SLOT_COUNT; i++) {
            if (cookingProgress[i] <= 0) continue;
            cookingProgress[i] = Math.max(0, cookingProgress[i] - 2);
        }
    }

    public List<ItemStack> takeAllItems() {
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < SLOT_COUNT; i++) {
            if (!slots[i].isEmpty()) {
                items.add(slots[i].clone());
                slots[i] = ItemStack.empty();
                cookingProgress[i] = 0;
                cookingTotalTime[i] = 0;
            }
        }
        return items;
    }

    static int getCampfireCookingTime(ItemStack input) {
        return CampfireRecipeUtil.getCookingTime(input,
                ConfigManager.getInt("stove.cooking.default_cook_time", 600));
    }

    static ItemStack getCampfireResult(ItemStack input) {
        return CampfireRecipeUtil.getResult(input);
    }

    public static boolean isCampfireIngredient(ItemStack input) {
        return CampfireRecipeUtil.isIngredient(input);
    }

    @Override
    public void saveCustomData(CompoundTag tag) {
        for (int i = 0; i < SLOT_COUNT; i++) {
            String byteKey = "slot_" + i + "_bytes";
            String idKey = "slot_" + i + "_id";
            String countKey = "slot_" + i + "_count";
            if (!slots[i].isEmpty()) {
                try { tag.putByteArray(byteKey, slots[i].serializeAsBytes()); } catch (Exception ignored) {}
                String id = CraftEngineUtil.getItemIdentifier(slots[i]);
                if (id != null) {
                    tag.putString(idKey, id);
                    tag.putInt(countKey, slots[i].getAmount());
                }
            }
        }
        tag.putIntArray("cooking_progress", cookingProgress.clone());
        tag.putIntArray("cooking_total_time", cookingTotalTime.clone());
    }

    @Override
    public void loadCustomData(CompoundTag tag) {
        for (int i = 0; i < SLOT_COUNT; i++) {
            String byteKey = "slot_" + i + "_bytes";
            String idKey = "slot_" + i + "_id";
            String countKey = "slot_" + i + "_count";
            slots[i] = ItemStack.empty();
            byte[] bytes = tag.getByteArray(byteKey);
            if (bytes != null && bytes.length > 0) {
                try { slots[i] = ItemStack.deserializeBytes(bytes); } catch (Exception ignored) {}
            }
            if (slots[i] == null || slots[i].isEmpty()) {
                String id = tag.getString(idKey);
                if (id != null && !id.isEmpty()) {
                    int count = Math.max(1, tag.getInt(countKey));
                    ItemStack created = CraftEngineUtil.createItem(id, count);
                    slots[i] = (created != null) ? created : ItemStack.empty();
                }
            }
        }
        int[] progress = tag.getIntArray("cooking_progress");
        if (progress != null && progress.length >= SLOT_COUNT) {
            System.arraycopy(progress, 0, cookingProgress, 0, SLOT_COUNT);
        }
        int[] totalTime = tag.getIntArray("cooking_total_time");
        if (totalTime != null && totalTime.length >= SLOT_COUNT) {
            System.arraycopy(totalTime, 0, cookingTotalTime, 0, SLOT_COUNT);
        }
    }

    public record CompletedSlot(int slot, ItemStack result) {}
}
