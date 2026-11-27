package com.gucardev.dynamicreportsentity.platform.flow.core;

import com.gucardev.dynamicreportsentity.platform.api.JsonLogic;

import com.gucardev.dynamicreportsentity.platform.flow.spi.StepResult;
import com.gucardev.dynamicreportsentity.platform.flow.spi.StepException;
import com.gucardev.dynamicreportsentity.platform.flow.spi.RunData;
import com.gucardev.dynamicreportsentity.platform.flow.spi.StepHandler;
import com.gucardev.dynamicreportsentity.platform.flow.spi.StepContext;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Map;

/** Evaluates the condition's JSON Logic over the run's data; outcome {@code true} or {@code false}. */
@Component
class ConditionStepHandler implements StepHandler {

    private final Clock clock;

    ConditionStepHandler(Clock clock) {
        this.clock = clock;
    }

    @Override
    public String type() {
        return ConditionStep.TYPE;
    }

    @Override
    public StepResult execute(StepContext context) {
        ConditionStep step = (ConditionStep) context.step().step();
        boolean result;
        try {
            result = JsonLogic.test(step.expression(), RunData.of(context.step(), clock));
        } catch (JsonLogic.InvalidRuleException | ArithmeticException e) {
            throw new StepException("EXPRESSION_FAILED", "The condition could not be evaluated: " + e.getMessage(),
                    false);
        }
        return StepResult.outcome(Map.of("result", result), String.valueOf(result));
    }
}
