package dev.tako.papersdelight.mechanic.cutting;

import dev.tako.papersdelight.ce.CraftEngineUtil;
import net.momirealms.craftengine.core.block.entity.BlockEntity;
import net.momirealms.craftengine.core.block.entity.BlockEntityController;
import net.momirealms.craftengine.core.world.CEWorld;
import net.momirealms.craftengine.core.world.chunk.CEChunk;
import net.momirealms.craftengine.libraries.nbt.CompoundTag;
import org.bukkit.inventory.ItemStack;

public final class CuttingBoardBlockEntityController extends BlockEntityController {

    private ItemStack item;
    private ItemStack tool;
    private boolean toolInserted;

    public CuttingBoardBlockEntityController(BlockEntity blockEntity) {
        super(blockEntity);
    }

    private static final int SLOT_LIMIT = 64;

    public ItemStack item() { return item; }
    public void item(ItemStack itemIn) {
        if (itemIn == null || itemIn.isEmpty()) {
            this.item = null;
            markUnsaved();
            return;
        }
        this.item = itemIn.clone();
        int maxStack = Math.min(SLOT_LIMIT, itemIn.getMaxStackSize());
        if (this.item.getAmount() > maxStack) {
            this.item.setAmount(maxStack);
        }
        markUnsaved();
    }

    public ItemStack tool() { return tool; }
    public void tool(ItemStack toolIn) {
        this.tool = (toolIn == null || toolIn.isEmpty()) ? null : toolIn.clone();
        if (this.tool != null) this.tool.setAmount(1);
        markUnsaved();
    }

    public boolean isToolInserted() { return toolInserted; }
    public void setToolInserted(boolean inserted) {
        this.toolInserted = inserted;
        markUnsaved();
    }

    public boolean hasItem() { return item != null && !item.isEmpty(); }
    public boolean hasTool() { return tool != null && !tool.isEmpty(); }

    @Override
    public void saveCustomData(CompoundTag tag) {
        if (hasItem()) {

            try { tag.putByteArray("cutting_item", item.serializeAsBytes()); } catch (Exception ignored) {}
            String id = CraftEngineUtil.getItemIdentifier(item);
            if (id != null) {
                tag.putString("cutting_item_id", id);
                tag.putInt("cutting_item_count", item.getAmount());
            }
        }
        if (hasTool()) {
            try { tag.putByteArray("cutting_tool", tool.serializeAsBytes()); } catch (Exception ignored) {}
            String id = CraftEngineUtil.getItemIdentifier(tool);
            if (id != null) {
                tag.putString("cutting_tool_id", id);
            }
            tag.putBoolean("cutting_tool_inserted", toolInserted);
        }
    }

    @Override
    public void loadCustomData(CompoundTag tag) {

        byte[] itemBytes = tag.getByteArray("cutting_item");
        if (itemBytes != null && itemBytes.length > 0) {
            try { this.item = ItemStack.deserializeBytes(itemBytes); } catch (Exception ignored) {}
        }

        if (this.item == null || this.item.isEmpty()) {
            String itemId = tag.getString("cutting_item_id");
            if (itemId != null && !itemId.isEmpty()) {
                int count = Math.max(1, tag.getInt("cutting_item_count"));
                this.item = CraftEngineUtil.createItem(itemId, count);
            }
        }

        byte[] toolBytes = tag.getByteArray("cutting_tool");
        if (toolBytes != null && toolBytes.length > 0) {
            try { this.tool = ItemStack.deserializeBytes(toolBytes); } catch (Exception ignored) {}
        }
        if (this.tool == null || this.tool.isEmpty()) {
            String toolId = tag.getString("cutting_tool_id");
            if (toolId != null && !toolId.isEmpty()) {
                this.tool = CraftEngineUtil.createItem(toolId, 1);
            }
        }
        if (hasTool()) {
            this.toolInserted = tag.getBoolean("cutting_tool_inserted");
        }
    }

    void markUnsaved() {
        CEWorld ceWorld = this.blockEntity.world();
        if (ceWorld == null) return;
        CEChunk chunk = ceWorld.getChunkAtIfLoaded(this.blockEntity.pos());
        if (chunk != null) {
            chunk.setUnsaved(true);
        }
    }
}
