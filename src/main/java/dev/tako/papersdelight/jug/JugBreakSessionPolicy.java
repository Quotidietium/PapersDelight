package dev.tako.papersdelight.jug;

final class JugBreakSessionPolicy {
    private JugBreakSessionPolicy() {
    }

    static Decision decide(boolean hasOpenMenu, boolean closeAlreadyRequested) {
        if (!hasOpenMenu) return Decision.PROCEED;
        return closeAlreadyRequested ? Decision.CANCEL : Decision.CANCEL_AND_REQUEST_OWNER_CLOSE;
    }

    enum Decision {
        PROCEED(false, false),
        CANCEL(true, false),
        CANCEL_AND_REQUEST_OWNER_CLOSE(true, true);

        private final boolean cancelBreak;
        private final boolean requestOwnerClose;

        Decision(boolean cancelBreak, boolean requestOwnerClose) {
            this.cancelBreak = cancelBreak;
            this.requestOwnerClose = requestOwnerClose;
        }

        boolean cancelBreak() {
            return cancelBreak;
        }

        boolean requestOwnerClose() {
            return requestOwnerClose;
        }
    }
}
