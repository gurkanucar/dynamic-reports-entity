package com.gucardev.dynamicreportsentity.platform.flow.core;

import com.gucardev.dynamicreportsentity.platform.api.JsonLogic;

import com.gucardev.dynamicreportsentity.platform.flow.spi.StepResult;
import com.gucardev.dynamicreportsentity.platform.flow.spi.StepException;
import com.gucardev.dynamicreportsentity.platform.flow.spi.RunData;
import com.gucardev.dynamicreportsentity.platform.flow.spi.StepHandler;
import com.gucardev.dynamicreportsentity.platform.flow.spi.StepContext;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Keeps the elements of {@code source} for which the expression is true; each element is {@code item}. */
@Component
class FilterStepHandler implements StepHandler {

    static final int MAX_ITEMS = 10_000;

    private final Clock clock;

    FilterStepHandler(Clock clock) {
        this.clock = clock;
    }

    @Override
    public String type() {
        return FilterStep.TYPE;
    }

    @Override
    public StepResult execute(StepContext context) {
        FilterStep step = (FilterStep) context.step().step();
        Map<String, Object> data = RunData.of(context.step(), clock);
        Object source = JsonLogic.lookup(data, step.source());
        if (source != null && !(source instanceof List<?>)) {
            throw new StepException("FILTER_SOURCE_NOT_A_LIST", "'" + step.source() + "' is not a list", false);
        }
        List<?> items = source == null ? List.of() : (List<?>) source;
        if (items.size() > MAX_ITEMS) {
            throw new StepException("FILTER_SOURCE_TOO_LARGE", "'" + step.source() + "' has more than " + MAX_ITEMS
                    + " elements", false);
        }
        List<Object> kept = new ArrayList<>();
        try {
            for (Object item : items) {
                Map<String, Object> itemData = new LinkedHashMap<>(data);
                itemData.put("item", item);
                if (JsonLogic.test(step.expression(), itemData)) {
                    kept.add(item);
                }
            }
        } catch (JsonLogic.InvalidRuleException | ArithmeticException e) {
            throw new StepException("EXPRESSION_FAILED", "The filter could not be evaluated: " + e.getMessage(), false);
        }
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("items", kept);
        output.put("count", kept.size());
        output.put("total", items.size());
        return StepResult.done(output);
    }
}
