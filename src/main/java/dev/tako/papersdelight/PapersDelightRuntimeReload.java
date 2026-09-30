package dev.tako.papersdelight;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

final class PapersDelightRuntimeReload {

    private final Logger logger;
    private final List<Step> steps;

    PapersDelightRuntimeReload(Logger logger, List<Step> steps) {
        this.logger = Objects.requireNonNull(logger, "logger");
        this.steps = List.copyOf(Objects.requireNonNull(steps, "steps"));
    }

    void commit() {
        commit(() -> { });
    }

    void commit(Runnable beforeRecover) {
        Objects.requireNonNull(beforeRecover, "beforeRecover");
        List<Step> touched = new ArrayList<>();
        for (Step step : steps) {
            touched.add(step);
            try {
                step.commit().run();
            } catch (Throwable failure) {
                try {
                    beforeRecover.run();
                } catch (Throwable stateFailure) {
                    failure.addSuppressed(stateFailure);
                    logger.log(Level.SEVERE, dev.tako.papersdelight.config.ConfigManager.getOr(
                            "runtime_reload_state_recover_fail", "恢复全局 reload 状态失败，仍继续恢复已触碰 manager"), stateFailure);
                }
                recover(touched, failure);
                throw new IllegalStateException("Runtime reload step failed: " + step.name(), failure);
            }
        }
    }

    private void recover(List<Step> touched, Throwable originalFailure) {
        for (int index = touched.size() - 1; index >= 0; index--) {
            Step step = touched.get(index);
            try {
                step.recover().run();
            } catch (Throwable recoveryFailure) {
                originalFailure.addSuppressed(recoveryFailure);
                logger.log(Level.SEVERE, dev.tako.papersdelight.config.ConfigManager.getOr(
                        "runtime_reload_step_recover_fail", "恢复 runtime reload 步骤失败，继续恢复其它 manager: %step%")
                        .replace("%step%", step.name()), recoveryFailure);
            }
        }
    }

    record Step(String name, Runnable commit, Runnable recover) {
        Step {
            name = Objects.requireNonNull(name, "name");
            commit = Objects.requireNonNull(commit, "commit");
            recover = Objects.requireNonNull(recover, "recover");
        }
    }
}
