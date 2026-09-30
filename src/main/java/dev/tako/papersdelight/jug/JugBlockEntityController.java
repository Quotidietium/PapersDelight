package dev.tako.papersdelight.jug;

import dev.tako.libuid.api.FluidRegistry;
import dev.tako.libuid.api.FluidStack;
import dev.tako.libuid.api.FluidStackCodec;
import dev.tako.libuid.api.FluidTank;
import dev.tako.papersdelight.ce.CraftEngineUtil;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.block.entity.BlockEntity;
import net.momirealms.craftengine.core.block.entity.BlockEntityController;
import net.momirealms.craftengine.core.block.entity.tick.BlockEntityTicker;
import net.momirealms.craftengine.core.world.BlockPos;
import net.momirealms.craftengine.core.world.CEWorld;
import net.momirealms.craftengine.libraries.nbt.CompoundTag;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

public final class JugBlockEntityController extends BlockEntityController {

    private static final String KEY_CAPACITY = "jug_capacity";
    private static final String KEY_FLUID = "jug_fluid";
    private static final String KEY_INVALID_LIBUID_FLUID = "jug_invalid_libuid_fluid";
    private static final String KEY_INPUT = "jug_input";
    private static final String KEY_OUTPUT = "jug_output";

    private final FluidTank tank;
    private ItemStack input;
    private ItemStack output;

    private byte[] invalidLibuidFluid;
    private int processingTime;
    private int processingTimeTotal;
    int hopperTicks;
    int lastPassTick;

    public JugBlockEntityController(BlockEntity blockEntity) {
        super(blockEntity);
        this.tank = new FluidTank(JugFluidLevel.CAPACITY) {
            @Override
            protected void onContentsChanged() {
                if (!fluid().isEmpty()) invalidLibuidFluid = null;
                JugBlockEntityController.this.markUnsaved();
            }
        };
    }

    @Override
    public <C extends BlockEntityController> BlockEntityTicker<C> createBlockEntityTicker(CEWorld world, ImmutableBlockState blockState) {
        return createTickerHelper(JugBlockEntityController::tick);
    }

    public static void tick(CEWorld world, BlockPos pos, ImmutableBlockState state, JugBlockEntityController controller) {
        JugManager manager = JugManager.instance;
        if (manager != null) manager.tickJug(controller, world, pos);
    }

    @Override
    public void onUnload() {
        JugManager manager = JugManager.instance;
        if (manager != null) manager.onControllerUnloaded(this);
    }

    public FluidTank tank() {
        return tank;
    }

    public FluidStack fluid() {
        return tank.fluid();
    }

    public int fluidAmount() {
        return tank.amount();
    }

    @Nullable
    public byte[] invalidLibuidFluid() {
        return copyInvalidFluid(invalidLibuidFluid);
    }

    public void invalidLibuidFluid(@Nullable byte[] bytes) {
        invalidLibuidFluid = copyInvalidFluid(bytes);
        markUnsaved();
    }

    @Nullable
    public ItemStack input() {
        return input;
    }

    public void input(@Nullable ItemStack stack) {
        input = normalize(stack);
        markUnsaved();
    }

    @Nullable
    public ItemStack output() {
        return output;
    }

    public void output(@Nullable ItemStack stack) {
        output = normalize(stack);
        markUnsaved();
    }

    public int processingTime() {
        return processingTime;
    }

    public void processingTime(int value) {
        processingTime = value;
    }

    public int processingTimeTotal() {
        return processingTimeTotal;
    }

    public void processingTimeTotal(int value) {
        processingTimeTotal = value;
    }

    public void resetProgress() {
        processingTime = 0;
        processingTimeTotal = 0;
    }

    public boolean hasInput() {
        return !isEmpty(input);
    }

    public boolean hasOutput() {
        return !isEmpty(output);
    }

    @Override
    public void saveCustomData(CompoundTag tag) {
        tag.putInt(KEY_CAPACITY, tank.capacity());
        byte[] fluidBytes = FluidStackCodec.toBinaryOptional(tank.fluid());
        if (fluidBytes.length > 0) tag.putByteArray(KEY_FLUID, fluidBytes);
        saveInvalidFluidData(tag, invalidLibuidFluid);
        saveItem(tag, KEY_INPUT, input);
        saveItem(tag, KEY_OUTPUT, output);
    }

    @Override
    public void loadCustomData(CompoundTag tag) {
        tank.setFluid(decodePersistedFluid(tag.getByteArray(KEY_FLUID)));
        invalidLibuidFluid = tank.isEmpty() ? loadInvalidFluidData(tag) : null;
        input = normalize(loadItem(tag, KEY_INPUT));
        output = normalize(loadItem(tag, KEY_OUTPUT));
        resetProgress();
    }

    static FluidStack decodePersistedFluid(@Nullable byte[] bytes) {
        if (bytes == null || bytes.length == 0) return FluidStack.EMPTY;
        try {
            FluidStack stack = FluidStackCodec.fromBinaryOptional(bytes);
            return stack.isEmpty() || !FluidRegistry.contains(stack.fluidKey().toString())
                    ? FluidStack.EMPTY
                    : stack.limitSize(JugFluidLevel.CAPACITY);
        } catch (Throwable ignored) {
            return FluidStack.EMPTY;
        }
    }

    static void saveInvalidFluidData(CompoundTag tag, @Nullable byte[] bytes) {
        if (bytes != null && bytes.length > 0) tag.putByteArray(KEY_INVALID_LIBUID_FLUID, bytes.clone());
    }

    @Nullable
    static byte[] loadInvalidFluidData(CompoundTag tag) {
        return copyInvalidFluid(tag.getByteArray(KEY_INVALID_LIBUID_FLUID));
    }

    @Nullable
    private static byte[] copyInvalidFluid(@Nullable byte[] bytes) {
        return bytes == null || bytes.length == 0 ? null : bytes.clone();
    }

    private static void saveItem(CompoundTag tag, String key, @Nullable ItemStack stack) {
        if (isEmpty(stack)) return;
        try {
            tag.putByteArray(key, stack.serializeAsBytes());
        } catch (Exception ignored) {

        }
        String id = CraftEngineUtil.getItemIdentifier(stack);
        if (id != null) tag.putString(key + "_id", id);
        tag.putInt(key + "_count", stack.getAmount());
    }

    @Nullable
    private static ItemStack loadItem(CompoundTag tag, String key) {
        byte[] bytes = tag.getByteArray(key);
        if (bytes != null && bytes.length > 0) {
            try {
                ItemStack stack = ItemStack.deserializeBytes(bytes);
                if (!isEmpty(stack)) return stack;
            } catch (Exception ignored) {

            }
        }
        String id = tag.getString(key + "_id");
        if (id == null || id.isEmpty()) return null;
        int count = tag.getInt(key + "_count");
        if (count <= 0) return null;
        return CraftEngineUtil.createItem(id, count);
    }

    @Nullable
    static ItemStack copySlot(@Nullable ItemStack stack) {
        return isEmpty(stack) ? null : stack.clone();
    }

    @Nullable
    private static ItemStack normalize(@Nullable ItemStack stack) {
        return copySlot(stack);
    }

    private static boolean isEmpty(@Nullable ItemStack stack) {
        return stack == null || stack.isEmpty();
    }

    public void markUnsaved() {
        if (blockEntity == null) return;
        var world = blockEntity.world();
        if (world == null) return;
        var chunk = world.getChunkAtIfLoaded(blockEntity.pos());
        if (chunk != null) chunk.setUnsaved(true);
    }
}
