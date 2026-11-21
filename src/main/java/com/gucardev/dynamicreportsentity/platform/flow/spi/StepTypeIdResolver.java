package com.gucardev.dynamicreportsentity.platform.flow.spi;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import tools.jackson.databind.DatabindContext;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.jsontype.impl.TypeIdResolverBase;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Maps the JSON {@code type} of a step to its record class, for every mapper (HTTP bodies, stored definitions).
 * Filled from the {@link StepType} beans at startup; an unknown type fails like any unknown Jackson subtype.
 */
public final class StepTypeIdResolver extends TypeIdResolverBase {

    private static final Map<String, Class<? extends FlowStep>> TYPES = new ConcurrentHashMap<>();

    /** Called by the engine for each {@link StepType}; a name used twice is a programming error. */
    public static void register(String name, Class<? extends FlowStep> type) {
        Class<? extends FlowStep> previous = TYPES.putIfAbsent(name, type);
        if (previous != null && !previous.equals(type)) {
            throw new IllegalStateException("Step type '" + name + "' is registered twice: " + previous + ", " + type);
        }
    }

    @Override
    public String idFromValue(DatabindContext context, Object value) {
        return ((FlowStep) value).typeName();
    }

    @Override
    public String idFromValueAndType(DatabindContext context, Object value, Class<?> suggestedType) {
        return value == null ? null : idFromValue(context, value);
    }

    @Override
    public JavaType typeFromId(DatabindContext context, String id) {
        Class<? extends FlowStep> type = id == null ? null : TYPES.get(id);
        return type == null ? null : context.constructType(type);
    }

    @Override
    public String getDescForKnownTypeIds() {
        return String.join(", ", TYPES.keySet().stream().sorted().toList());
    }

    @Override
    public JsonTypeInfo.Id getMechanism() {
        return JsonTypeInfo.Id.CUSTOM;
    }
}
