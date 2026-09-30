package dev.tako.papersdelight.cookingpot;

import dev.tako.papersdelight.ce.CraftEngineUtil;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.block.entity.BlockEntity;
import net.momirealms.craftengine.core.block.entity.BlockEntityController;
import net.momirealms.craftengine.core.block.entity.tick.BlockEntityTicker;
import net.momirealms.craftengine.core.world.BlockPos;
import net.momirealms.craftengine.core.world.CEWorld;
import net.momirealms.craftengine.libraries.nbt.CompoundTag;
import org.bukkit.inventory.ItemStack;

public final class CookingPotBlockEntityController extends BlockEntityController {

    public static final int INGREDIENT_SLOTS = 6;

    private final ItemStack[] ingredients = new ItemStack[INGREDIENT_SLOTS];
    private ItemStack waitingOutput;
    private ItemStack finalOutput;
    private ItemStack utensil;
    private String recipeContainer;
    private int cookTime;
    private int cookTimeTotal;
    private float storedExperience;
    int particleTicks;
    int hopperTicks;
    int lastPassTick;
    private boolean heated;
    private int heatTicks;

    public CookingPotBlockEntityController(BlockEntity blockEntity) {
        super(blockEntity);
    }

    @Override
    public <C extends BlockEntityController> BlockEntityTicker<C> createBlockEntityTicker(CEWorld world, ImmutableBlockState blockState) {
        return createTickerHelper(CookingPotBlockEntityController::tick);
    }

    public static void tick(CEWorld world, BlockPos pos, ImmutableBlockState state, CookingPotBlockEntityController controller) {
        CookingPotManager manager = CookingPotManager.instance;
        if (manager != null) manager.potTick(controller, world, pos);
    }

    @Override
    public void onLoad() {
        CookingPotManager manager = CookingPotManager.instance;
        if (manager != null) manager.registerPot(this);
    }

    @Override
    public void onUnload() {
        CookingPotManager manager = CookingPotManager.instance;
        if (manager != null) manager.forgetPot(this);
    }

    public ItemStack ingredient(int slot) {
        return slot >= 0 && slot < INGREDIENT_SLOTS ? ingredients[slot] : null;
    }

    public void ingredient(int slot, ItemStack stack) {
        if (slot < 0 || slot >= INGREDIENT_SLOTS) return;
        ingredients[slot] = normalize(stack);
        markUnsaved();
    }

    public ItemStack[] allIngredients() { return ingredients; }

    public ItemStack waitingOutput() { return waitingOutput; }
    public void waitingOutput(ItemStack stack) {
        this.waitingOutput = normalize(stack);
        markUnsaved();
    }

    public ItemStack finalOutput() { return finalOutput; }
    public void finalOutput(ItemStack stack) {
        this.finalOutput = normalize(stack);
        markUnsaved();
    }

    public ItemStack utensil() { return utensil; }
    public void utensil(ItemStack stack) {
        this.utensil = normalize(stack);
        markUnsaved();
    }

    public String recipeContainer() { return recipeContainer; }
    public void recipeContainer(String id) {
        this.recipeContainer = (id == null || id.isEmpty()) ? null : id;
        markUnsaved();
    }

    public int cookTime() { return cookTime; }
    public void cookTime(int time) { this.cookTime = time; markUnsaved(); }
    public int cookTimeTotal() { return cookTimeTotal; }
    public void cookTimeTotal(int total) { this.cookTimeTotal = total; markUnsaved(); }
    public float storedExperience() { return storedExperience; }
    public void storedExperience(float exp) { this.storedExperience = exp; markUnsaved(); }

    public boolean isCooking() { return cookTime > 0 && cookTime < cookTimeTotal; }
    public boolean heated() { return heated; }

    public void heated(boolean value) { heated = value; }

    public int heatTicks() { return heatTicks; }

    public void heatTicks(int value) { heatTicks = value; }

    public boolean hasWaitingOutput() { return !isEmpty(waitingOutput); }
    public boolean hasFinalOutput() { return !isEmpty(finalOutput); }
    public boolean hasUtensil() { return !isEmpty(utensil); }
    public boolean hasAnyIngredient() {
        for (ItemStack ing : ingredients) if (!isEmpty(ing)) return true;
        return false;
    }

    public CookingPotData toData() {
        CookingPotData data = new CookingPotData();
        for (int i = 0; i < INGREDIENT_SLOTS; i++) {
            if (ingredients[i] != null) data.ingredients[i] = ingredients[i].clone();
        }
        data.waitingOutput = waitingOutput != null ? waitingOutput.clone() : null;
        data.finalOutput = finalOutput != null ? finalOutput.clone() : null;
        data.utensil = utensil != null ? utensil.clone() : null;
        data.recipeContainer = recipeContainer;
        data.cookTime = cookTime;
        data.cookTimeTotal = cookTimeTotal;
        data.storedExperience = storedExperience;
        data.isCooking = isCooking();
        if (cookTimeTotal > 0) {
            data.progress = Math.min(100, (int) ((cookTime / (float) cookTimeTotal) * 100));
        }
        return data;
    }

    public void fromData(CookingPotData data) {
        for (int i = 0; i < INGREDIENT_SLOTS; i++) {
            ingredients[i] = normalize(data.ingredients[i]);
        }
        this.waitingOutput = normalize(data.waitingOutput);
        this.finalOutput = normalize(data.finalOutput);
        this.utensil = normalize(data.utensil);
        this.recipeContainer = data.recipeContainer;
        this.cookTime = data.cookTime;
        this.cookTimeTotal = data.cookTimeTotal;
        this.storedExperience = data.storedExperience;
        markUnsaved();
    }

    @Override
    public void saveCustomData(CompoundTag tag) {
        for (int i = 0; i < INGREDIENT_SLOTS; i++) {
            saveItem(tag, "pot_ing_" + i, ingredients[i]);
        }
        saveItem(tag, "pot_waiting", waitingOutput);
        saveItem(tag, "pot_final", finalOutput);
        saveItem(tag, "pot_utensil", utensil);
        if (recipeContainer != null) tag.putString("pot_container", recipeContainer);
        tag.putInt("pot_cook_time", cookTime);
        tag.putInt("pot_cook_total", cookTimeTotal);
        tag.putFloat("pot_exp", storedExperience);
    }

    @Override
    public void loadCustomData(CompoundTag tag) {
        for (int i = 0; i < INGREDIENT_SLOTS; i++) {
            ingredients[i] = loadItem(tag, "pot_ing_" + i);
        }
        waitingOutput = loadItem(tag, "pot_waiting");
        finalOutput = loadItem(tag, "pot_final");
        utensil = loadItem(tag, "pot_utensil");
        recipeContainer = tag.getString("pot_container");
        if (recipeContainer != null && recipeContainer.isEmpty()) recipeContainer = null;
        cookTime = tag.getInt("pot_cook_time");
        cookTimeTotal = tag.getInt("pot_cook_total");
        storedExperience = tag.getFloat("pot_exp");
    }

    private static void saveItem(CompoundTag tag, String key, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        try {
            tag.putByteArray(key, stack.serializeAsBytes());
        } catch (Exception ignored) {}
        String id = CraftEngineUtil.getItemIdentifier(stack);
        if (id != null) {
            tag.putString(key + "_id", id);
            tag.putInt(key + "_count", stack.getAmount());
        }
    }

    private static ItemStack loadItem(CompoundTag tag, String key) {
        byte[] bytes = tag.getByteArray(key);
        if (bytes != null && bytes.length > 0) {
            try {
                ItemStack s = ItemStack.deserializeBytes(bytes);
                if (s != null && !s.isEmpty()) return s;
            } catch (Exception ignored) {}
        }
        String id = tag.getString(key + "_id");
        if (id != null && !id.isEmpty()) {
            int count = Math.max(1, tag.getInt(key + "_count"));
            if (count <= 0) count = 1;
            ItemStack created = CraftEngineUtil.createItem(id, count);
            return (created != null) ? created : null;
        }
        return null;
    }

    private static ItemStack normalize(ItemStack stack) {
        return (stack == null || stack.isEmpty()) ? null : stack.clone();
    }

    private static boolean isEmpty(ItemStack stack) {
        return stack == null || stack.isEmpty();
    }

    private void markUnsaved() {
        net.momirealms.craftengine.core.world.CEWorld ceWorld = this.blockEntity.world();
        if (ceWorld == null) return;
        net.momirealms.craftengine.core.world.chunk.CEChunk chunk = ceWorld.getChunkAtIfLoaded(this.blockEntity.pos());
        if (chunk != null) {
            chunk.setUnsaved(true);
        }
    }
}
