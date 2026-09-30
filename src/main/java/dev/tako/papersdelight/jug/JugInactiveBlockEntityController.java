package dev.tako.papersdelight.jug;

import net.momirealms.craftengine.core.block.entity.BlockEntity;
import net.momirealms.craftengine.core.block.entity.BlockEntityController;
import net.momirealms.craftengine.libraries.nbt.CompoundTag;
import net.momirealms.craftengine.libraries.nbt.Tag;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public final class JugInactiveBlockEntityController extends BlockEntityController {

    @Nullable
    private CompoundTag retained;

    public JugInactiveBlockEntityController(@Nullable BlockEntity blockEntity) {
        super(blockEntity);
    }

    @Override
    public void loadCustomData(CompoundTag data) {
        this.retained = data == null || data.isEmpty() ? null : data.copy();
    }

    @Override
    public void saveCustomData(CompoundTag data) {
        CompoundTag snapshot = this.retained;
        if (snapshot == null || data == null) return;
        for (Map.Entry<String, Tag> entry : snapshot.entrySet()) {
            data.put(entry.getKey(), entry.getValue());
        }
    }
}
