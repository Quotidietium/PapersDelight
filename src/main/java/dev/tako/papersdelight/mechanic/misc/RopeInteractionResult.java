package dev.tako.papersdelight.mechanic.misc;

import net.momirealms.craftengine.core.entity.player.InteractionResult;

final class RopeInteractionResult {

    private RopeInteractionResult() {
    }

    static InteractionResult afterSuccessfulAction(boolean reeling) {
        return reeling ? InteractionResult.SUCCESS_AND_CANCEL : InteractionResult.SUCCESS;
    }
}
