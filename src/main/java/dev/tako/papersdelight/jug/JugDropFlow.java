package dev.tako.papersdelight.jug;

final class JugDropFlow {
    private JugDropFlow() {
    }

    static void settleContents(boolean dropContents, Runnable dropInput, Runnable dropOutput, Runnable clearContents) {
        if (dropContents) {
            dropInput.run();
            dropOutput.run();
        }
        clearContents.run();
    }
}
