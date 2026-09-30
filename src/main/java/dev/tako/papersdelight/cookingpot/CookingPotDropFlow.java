package dev.tako.papersdelight.cookingpot;

import dev.tako.papersdelight.common.ExplosionSettleFlow;
import dev.tako.papersdelight.common.ExplosionStaging;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

final class CookingPotDropFlow<E, B> {
    interface Drops<B> { void drop(B block, boolean contents, boolean body); }

    private final ExplosionStaging<E, B> staging = new ExplosionStaging<>();

    void stage(E event, B block) { staging.stage(event, block); }
    boolean stageIf(E event, B block, Predicate<B> identity) {
        if (!identity.test(block)) return false;
        staging.stage(event, block);
        return true;
    }

    void settle(E event, boolean cancelled, Predicate<B> guiActive, Predicate<B> survives,
                Drops<B> drops, Consumer<B> remove) {
        settle(event, cancelled, ignored -> true, guiActive, survives, drops, remove);
    }

    void settle(E event, boolean cancelled, Predicate<B> valid, Predicate<B> guiActive,
                Predicate<B> survives, Drops<B> drops, Consumer<B> remove) {
        List<B> entries = staging.drain(event);
        ExplosionSettleFlow.settleStaged(entries, cancelled, block -> {
            if (!valid.test(block) || guiActive.test(block)) return;
            boolean drop = survives.test(block);
            drops.drop(block, drop, drop);
            remove.accept(block);
        });
    }
}
