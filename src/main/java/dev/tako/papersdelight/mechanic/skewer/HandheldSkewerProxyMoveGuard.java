package dev.tako.papersdelight.mechanic.skewer;

final class HandheldSkewerProxyMoveGuard {
    private HandheldSkewerProxyMoveGuard() {
    }

    static boolean blocksClick(boolean sessionActive, boolean currentIsProxy, boolean cursorIsProxy,
                               boolean hotbarOrOffhandIsProxy) {
        return sessionActive && (currentIsProxy || cursorIsProxy || hotbarOrOffhandIsProxy);
    }

    static boolean blocksDrag(boolean sessionActive, boolean oldCursorIsProxy, boolean draggedItemIsProxy) {
        return sessionActive && (oldCursorIsProxy || draggedItemIsProxy);
    }

    static boolean blocksSwap(boolean sessionActive, boolean mainHandIsProxy, boolean offHandIsProxy) {
        return sessionActive && (mainHandIsProxy || offHandIsProxy);
    }
}
