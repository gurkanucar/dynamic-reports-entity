package com.gucardev.dynamicreportsentity.platform.flow.core;

import com.gucardev.dynamicreportsentity.platform.flow.spi.FlowStep;
import com.gucardev.dynamicreportsentity.platform.flow.spi.StepReference;
import com.gucardev.dynamicreportsentity.platform.flow.spi.StepType;
import com.gucardev.dynamicreportsentity.platform.flow.spi.StepTypeIdResolver;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Every {@link StepType} bean, by name; registers them for JSON so stored flows and requests can be read. */
@Component
public class StepTypes {

    private final Map<String, StepType<?>> types;

    StepTypes(List<StepType<?>> types) {
        types.forEach(type -> StepTypeIdResolver.register(type.name(), type.stepClass()));
        this.types = types.stream().collect(Collectors.toUnmodifiableMap(StepType::name, Function.identity()));
    }

    @SuppressWarnings("unchecked")
    <S extends FlowStep> StepType<S> of(S step) {
        StepType<?> type = types.get(step.typeName());
        if (type == null) {
            throw new IllegalStateException("No step type '" + step.typeName() + "'");
        }
        return (StepType<S>) type;
    }

    boolean producesFiles(FlowStep step) {
        return step != null && of(step).producesFiles();
    }

    /** What the steps of a flow use in other modules. */
    Set<StepReference> references(FlowDefinition definition) {
        Set<StepReference> references = new HashSet<>();
        for (FlowStep step : definition.steps()) {
            references.addAll(of(step).references(step));
        }
        return references;
    }

    Set<String> names() {
        return types.keySet();
    }
}
