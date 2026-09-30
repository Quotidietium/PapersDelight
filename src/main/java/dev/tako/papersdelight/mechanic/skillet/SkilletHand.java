package dev.tako.papersdelight.mechanic.skillet;

import net.momirealms.craftengine.core.entity.player.InteractionHand;
import org.bukkit.inventory.EquipmentSlot;

final class SkilletHand {
    private SkilletHand() {
    }

    static EquipmentSlot toEquipmentSlot(InteractionHand hand) {
        return hand == InteractionHand.OFF_HAND ? EquipmentSlot.OFF_HAND : EquipmentSlot.HAND;
    }
}
