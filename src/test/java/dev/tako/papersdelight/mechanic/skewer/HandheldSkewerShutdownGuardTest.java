package dev.tako.papersdelight.mechanic.skewer;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HandheldSkewerShutdownGuardTest {

    @Test
    void settlesSynchronouslyWhenPluginDisabled() {
        List<String> settled = new ArrayList<>();
        List<String> dispatched = new ArrayList<>();

        boolean wasSynchronous = ShutdownDispatch.settle(
                false, "alice", settled::add, dispatched::add);

        assertTrue(wasSynchronous, "插件已禁用时应走同步路径");
        assertEquals(List.of("alice"), settled, "应同步结算目标");
        assertTrue(dispatched.isEmpty(), "插件已禁用时不得派发调度任务");
    }

    @Test
    void dispatchesWhilePluginStillEnabled() {
        List<String> settled = new ArrayList<>();
        List<String> dispatched = new ArrayList<>();

        boolean wasSynchronous = ShutdownDispatch.settle(
                true, "bob", settled::add, dispatched::add);

        assertFalse(wasSynchronous, "插件仍启用时应走调度路径");
        assertEquals(List.of("bob"), dispatched, "应派发调度任务");
        assertTrue(settled.isEmpty(), "插件仍启用时不应绕过调度器");
    }

    @Test
    void synchronousFailureDoesNotAbortShutdown() {
        Consumer<String> exploding = target -> {
            throw new IllegalStateException("settle failed for " + target);
        };

        boolean wasSynchronous = ShutdownDispatch.settle(
                false, "carol", exploding, target -> { });

        assertTrue(wasSynchronous, "同步结算抛异常时仍应视为已走同步路径，不得向上传播");
    }

    @Test
    void everyTargetIsSettledWhenDisabled() {
        List<String> settled = new ArrayList<>();
        List<String> players = List.of("alice", "bob", "carol");

        for (String player : players) {
            ShutdownDispatch.settle(false, player, settled::add, target -> { });
        }

        assertEquals(players, settled, "关停时每个在线玩家都应被结算");
    }

    @Test
    void oneFailingTargetDoesNotBlockRemaining() {
        List<String> settled = new ArrayList<>();
        Consumer<String> flaky = target -> {
            if ("bob".equals(target)) throw new IllegalStateException("boom");
            settled.add(target);
        };

        for (String player : List.of("alice", "bob", "carol")) {
            ShutdownDispatch.settle(false, player, flaky, target -> { });
        }

        assertEquals(List.of("alice", "carol"), settled,
                "单个玩家结算失败不应中断其余玩家的结算");
    }
}
