package com.gucardev.dynamicreportsentity.platform.flow.core;

import java.util.UUID;
import com.gucardev.dynamicreportsentity.platform.flow.spi.WaitingSteps;
import org.springframework.stereotype.Component;

import java.util.Map;

/** {@link WaitingSteps} for other modules: resumes the step and wakes the engine for the next one. */
@Component
class WaitingStepsService implements WaitingSteps {

    private final RunStore store;
    private final FlowEngine engine;

    WaitingStepsService(RunStore store, FlowEngine engine) {
        this.store = store;
        this.engine = engine;
    }

    @Override
    public boolean resume(UUID stepRunId, String outcome, Map<String, Object> output, String eventType,
                          String message) {
        boolean resumed = store.resume(stepRunId, outcome, output, eventType, message);
        if (resumed) {
            engine.wakeUp();
        }
        return resumed;
    }
}
