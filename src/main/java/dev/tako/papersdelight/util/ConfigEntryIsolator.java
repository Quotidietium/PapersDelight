package dev.tako.papersdelight.util;

public final class ConfigEntryIsolator {

    private ConfigEntryIsolator() {
    }

    public static <T> void forEach(Iterable<? extends T> entries,
                                   ThrowingIndexedConsumer<? super T> action,
                                   FailureHandler<? super T> failureHandler) {
        int index = 0;
        for (T entry : entries) {
            int currentIndex = ++index;
            try {
                action.accept(entry, currentIndex);
            } catch (Exception exception) {
                failureHandler.onFailure(entry, currentIndex, exception);
            }
        }
    }

    @FunctionalInterface
    public interface ThrowingIndexedConsumer<T> {
        void accept(T entry, int index) throws Exception;
    }

    @FunctionalInterface
    public interface FailureHandler<T> {
        void onFailure(T entry, int index, Exception exception);
    }
}
