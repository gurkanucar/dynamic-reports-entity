package com.gucardev.dynamicreportsentity.platform.api;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Behaviour follows https://jsonlogic.com/operations.html for the operators we support. */
class JsonLogicTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static Object eval(String rule, String data) {
        return JsonLogic.apply(JSON.readValue(rule, Object.class), JSON.readValue(data, Object.class));
    }

    @Test
    void comparesAndCombines() {
        String data = """
                {"trigger": {"status": "APPROVED", "amount": 1500, "country": "TR"}, "steps": {"dataset": {"rowCount": 0}}}""";
        assertThat(eval("""
                {"and": [{"==": [{"var": "trigger.status"}, "APPROVED"]}, {">": [{"var": "trigger.amount"}, 1000]}]}""", data))
                .isEqualTo(true);
        assertThat(eval("""
                {"in": [{"var": "trigger.country"}, ["TR", "DE"]]}""", data)).isEqualTo(true);
        assertThat(JsonLogic.test(JSON.readValue("""
                {">": [{"var": "steps.dataset.rowCount"}, 0]}""", Object.class), JSON.readValue(data, Object.class))).isFalse();
        assertThat(eval("""
                {"<=": [1000, {"var": "trigger.amount"}, 2000]}""", data)).isEqualTo(true);
        assertThat(eval("""
                {"or": [false, 0, "x"]}""", "{}")).isEqualTo("x");
        assertThat(eval("""
                {"==": [1, "1"]}""", "{}")).isEqualTo(true);
        assertThat(eval("""
                {"===": [1, "1"]}""", "{}")).isEqualTo(false);
        assertThat(eval("""
                {"<": ["2026-01-31", {"var": "d"}]}""", """
                {"d": "2026-02-01"}""")).isEqualTo(true);
    }

    @Test
    void worksOnArraysAndNumbers() {
        String data = """
                {"items": [{"amount": 50, "ok": true}, {"amount": 500, "ok": false}, {"amount": 5000, "ok": true}]}""";
        assertThat(eval("""
                {"filter": [{"var": "items"}, {">": [{"var": "amount"}, 100]}]}""", data))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST).hasSize(2);
        assertThat(eval("""
                {"reduce": [{"var": "items"}, {"+": [{"var": "current.amount"}, {"var": "accumulator"}]}, 0]}""", data))
                .isEqualTo(5550L);
        assertThat(eval("""
                {"some": [{"var": "items"}, {"!": {"var": "ok"}}]}""", data)).isEqualTo(true);
        assertThat(eval("""
                {"all": [{"var": "items"}, {"var": "ok"}]}""", data)).isEqualTo(false);
        assertThat(eval("""
                {"/": [7, 2]}""", "{}")).isEqualTo(3.5);
        assertThat(eval("""
                {"if": [{"var": "missing"}, "a", {"var": "x"}, "b", "c"]}""", """
                {"x": 0}""")).isEqualTo("c");
        assertThat(eval("""
                {"var": ["nope", "default"]}""", "{}")).isEqualTo("default");
        assertThat(eval("""
                {"var": "list.1"}""", """
                {"list": ["a", "b"]}""")).isEqualTo("b");
        assertThat(eval("""
                {"missing": ["a", "b"]}""", """
                {"a": 1}""")).isEqualTo(List.of("b"));
        assertThat(eval("""
                {"cat": ["Order ", {"var": "id"}]}""", """
                {"id": 42}""")).isEqualTo("Order 42");
    }

    @Test
    void onlyKnownOperatorsAreAccepted() {
        JsonLogic.check(JSON.readValue("""
                {"and": [{">": [{"var": "a"}, 1]}, {"in": ["x", {"var": "b"}]}]}""", Object.class));
        assertThatThrownBy(() -> JsonLogic.check(JSON.readValue("""
                {"exec": ["rm -rf /"]}""", Object.class)))
                .isInstanceOf(JsonLogic.InvalidRuleException.class).hasMessageContaining("exec");
        assertThatThrownBy(() -> JsonLogic.check(Map.of("==", 1, "!=", 2)))
                .isInstanceOf(JsonLogic.InvalidRuleException.class);
    }
}
