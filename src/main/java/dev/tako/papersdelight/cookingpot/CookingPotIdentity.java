package dev.tako.papersdelight.cookingpot;

import java.util.function.Function;
import java.util.function.Predicate;

final class CookingPotIdentity<B, H> {
    private final Function<B, H> behaviorLookup;
    private final Predicate<H> behaviorMatch;
    private final Predicate<B> controllerPresent;

    CookingPotIdentity(Function<B, H> behaviorLookup, Predicate<H> behaviorMatch,
                       Predicate<B> controllerPresent) {
        this.behaviorLookup = behaviorLookup;
        this.behaviorMatch = behaviorMatch;
        this.controllerPresent = controllerPresent;
    }

    boolean isPotBehavior(B block) {
        H behavior = behaviorLookup.apply(block);
        return behavior != null && behaviorMatch.test(behavior);
    }

    boolean isCurrentPot(B block) {
        return isPotBehavior(block) && controllerPresent.test(block);
    }
}
