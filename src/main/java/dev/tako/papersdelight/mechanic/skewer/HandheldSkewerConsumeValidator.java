package dev.tako.papersdelight.mechanic.skewer;

import org.bukkit.inventory.EquipmentSlot;

import java.util.Arrays;
import java.util.UUID;

final class HandheldSkewerConsumeValidator {
    private HandheldSkewerConsumeValidator() {
    }

    static Decision decide(UUID sessionId, EquipmentSlot sessionHand, String cookingProxy, byte[] sessionSource,
                           UUID eventId, EquipmentSlot eventHand, String eventItemId, byte[] eventSource,
                           UUID heldId, String heldItemId, byte[] heldSource,
                           int elapsedTicks, int totalTicks, boolean carriesFeaturePdc) {
        if (!carriesFeaturePdc) return Decision.PASS_THROUGH;
        if (!matches(sessionId, sessionHand, cookingProxy, sessionSource,
                eventId, eventHand, eventItemId, eventSource, heldId, heldItemId, heldSource)) {
            return Decision.RECOVER;
        }
        return elapsedTicks >= Math.max(1, totalTicks) ? Decision.COMPLETE : Decision.KEEP_SESSION;
    }

    private static boolean matches(UUID sessionId, EquipmentSlot sessionHand, String cookingProxy, byte[] sessionSource,
                                   UUID eventId, EquipmentSlot eventHand, String eventItemId, byte[] eventSource,
                                   UUID heldId, String heldItemId, byte[] heldSource) {
        return sessionId != null
                && cookingProxy != null
                && sessionSource != null
                && sessionSource.length > 0
                && sessionId.equals(eventId)
                && sessionId.equals(heldId)
                && sessionHand == eventHand
                && cookingProxy.equals(eventItemId)
                && cookingProxy.equals(heldItemId)
                && Arrays.equals(sessionSource, eventSource)
                && Arrays.equals(sessionSource, heldSource);
    }

    enum Decision {
        PASS_THROUGH(false, false),
        KEEP_SESSION(true, false),
        COMPLETE(true, true),
        RECOVER(true, false);

        private final boolean cancelsEvent;
        private final boolean producesResult;

        Decision(boolean cancelsEvent, boolean producesResult) {
            this.cancelsEvent = cancelsEvent;
            this.producesResult = producesResult;
        }

        boolean cancelsEvent() {
            return cancelsEvent;
        }

        boolean producesResult() {
            return producesResult;
        }
    }
}
