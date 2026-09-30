package dev.tako.papersdelight.mechanic.skillet;

import net.momirealms.craftengine.core.entity.player.InteractionHand;
import org.bukkit.inventory.EquipmentSlot;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SkilletInteractionHandTest {

    @Test
    void mapsMainHandToBukkitMainHand() {
        assertEquals(EquipmentSlot.HAND, SkilletHand.toEquipmentSlot(InteractionHand.MAIN_HAND));
    }

    @Test
    void mapsOffHandToBukkitOffHand() {
        assertEquals(EquipmentSlot.OFF_HAND, SkilletHand.toEquipmentSlot(InteractionHand.OFF_HAND));
    }
}
