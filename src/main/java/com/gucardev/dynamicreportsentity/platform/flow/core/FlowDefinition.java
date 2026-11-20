package com.gucardev.dynamicreportsentity.platform.flow.core;

import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

/**
 * What a flow does, as stored in the database. A run keeps its own copy, so editing a flow never changes
 * runs that already started.
 */
public record FlowDefinition(List<FlowStep> steps) {

    static final JsonMapper JSON = JsonMapper.builder().build();

    public FlowDefinition {
        steps = List.copyOf(steps);
    }

    public static FlowDefinition fromJson(String json) {
        return JSON.readValue(json, FlowDefinition.class);
    }

    public String toJson() {
        return JSON.writeValueAsString(this);
    }

    static String toJson(Map<String, ?> output) {
        return JSON.writeValueAsString(output);
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> outputFromJson(String json) {
        return json == null ? Map.of() : JSON.readValue(json, Map.class);
    }
}
