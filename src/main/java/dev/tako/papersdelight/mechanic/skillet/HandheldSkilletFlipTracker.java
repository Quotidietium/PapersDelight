package dev.tako.papersdelight.mechanic.skillet;

final class HandheldSkilletFlipTracker {

    private boolean jumpPending;
    private boolean airborne;
    private int sizzleBurstTicks;

    void onJump() {
        jumpPending = true;
    }

    boolean updateLanding(boolean onGround) {
        if (jumpPending && !onGround) {
            jumpPending = false;
            airborne = true;
            return false;
        }
        if (airborne && onGround) {
            airborne = false;
            sizzleBurstTicks = 2;
            return true;
        }
        return false;
    }

    boolean consumeSizzleBurst() {
        if (sizzleBurstTicks <= 0) return false;
        sizzleBurstTicks--;
        return true;
    }
}
