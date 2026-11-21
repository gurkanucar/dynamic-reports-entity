package com.gucardev.dynamicreportsentity.platform.flow.core;

import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

/**
 * What a flow does, as stored in the database. A run keeps its own copy, so editing a flow never changes runs that
 * already started.
 *
 * @param edges  connections between steps; empty = the steps run in their order (simple flows, older flows)
 * @param layout where each step sits on the editor's canvas (by step key); only for the UI
 */
public record FlowDefinition(List<FlowStep> steps, List<FlowEdge> edges, Map<String, Position> layout) {

    /** A step's position on the canvas. */
    public record Position(double x, double y) {
    }

    static final JsonMapper JSON = JsonMapper.builder().build();

    public FlowDefinition {
        steps = List.copyOf(steps);
        edges = edges == null ? List.of() : List.copyOf(edges);
        layout = layout == null ? Map.of() : Map.copyOf(layout);
    }

    public FlowDefinition(List<FlowStep> steps) {
        this(steps, List.of(), Map.of());
    }

    public static FlowDefinition fromJson(String json) {
        return JSON.readValue(json, FlowDefinition.class);
    }

    public String toJson() {
        return JSON.writeValueAsString(this);
    }

    static String toJson(Object value) {
        return JSON.writeValueAsString(value);
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> outputFromJson(String json) {
        return json == null ? Map.of() : JSON.readValue(json, Map.class);
    }

    static Object valueFromJson(String json) {
        return json == null ? null : JSON.readValue(json, Object.class);
    }
}
