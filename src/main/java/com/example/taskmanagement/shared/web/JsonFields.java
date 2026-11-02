package com.example.taskmanagement.shared.web;

import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import tools.jackson.databind.JsonNode;

/** Strict request fields; omission and explicit null remain distinct and errors belong to the calling module. */
public final class JsonFields {
    private final JsonNode body;
    private final Supplier<? extends RuntimeException> invalid;

    public JsonFields(JsonNode body, Supplier<? extends RuntimeException> invalid) {
        this.invalid = Objects.requireNonNull(invalid, "invalid");
        if (body == null || !body.isObject()) {
            throw invalid.get();
        }
        this.body = body;
    }

    public JsonFields only(Set<String> allowed) {
        if (body.propertyNames().stream().anyMatch(name -> !allowed.contains(name))) {
            throw invalid.get();
        }
        return this;
    }

    public String text(String name, String fallback) {
        return optional(name, fallback, node -> {
            if (!node.isTextual()) {
                throw invalid.get();
            }
            return node.asString();
        });
    }

    public Long number(String name, Long fallback) {
        return optional(name, fallback, node -> {
            if (!node.isIntegralNumber() || !node.canConvertToLong()) {
                throw invalid.get();
            }
            return node.longValue();
        });
    }

    public long requiredNumber(String name, long minimum) {
        Long value = number(name, null);
        if (value == null || value < minimum) {
            throw invalid.get();
        }
        return value;
    }

    private <T> T optional(String name, T fallback, Function<JsonNode, T> reader) {
        var node = body.get(name);
        if (node == null) {
            return fallback;
        }
        if (node.isNull()) {
            return null;
        }
        return reader.apply(node);
    }
}
