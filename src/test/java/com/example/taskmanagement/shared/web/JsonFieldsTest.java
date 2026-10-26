package com.example.taskmanagement.shared.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class JsonFieldsTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void omissionRetainsFallbackButExplicitNullClearsOptionalFields() {
        var fields = fields("{\"clear\":null}");
        assertThat(fields.text("omitted", "previous")).isEqualTo("previous");
        assertThat(fields.text("clear", "previous")).isNull();
        assertThat(fields.number("omitted", 7L)).isEqualTo(7L);
        assertThat(fields.number("clear", 7L)).isNull();
    }

    @Test
    void acceptsExactLongBoundsWithoutFloatingPointOrStringCoercion() {
        assertThat(fields("{\"id\":9223372036854775807}").requiredNumber("id", 1)).isEqualTo(Long.MAX_VALUE);
        assertThat(fields("{\"revision\":0}").requiredNumber("revision", 0)).isZero();
        for (String value : new String[] {"9223372036854775808", "1.0", "\"1\"", "true", "{}", "[]"}) {
            assertThatThrownBy(() -> fields("{\"id\":" + value + "}").requiredNumber("id", 1))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void rejectsMissingNullAndBelowMinimumRequiredValues() {
        for (String body : new String[] {"{}", "{\"id\":null}", "{\"id\":0}", "{\"id\":-1}"}) {
            assertThatThrownBy(() -> fields(body).requiredNumber("id", 1))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void rejectsNonObjectsUnknownFieldsAndWrongTextTypesWithTheModulesOwnError() {
        var failure = new IllegalStateException("module validation code");
        for (String body : new String[] {"null", "[]", "1", "\"text\""}) {
            assertThatThrownBy(() -> new JsonFields(json.readTree(body), () -> failure)).isSameAs(failure);
        }
        assertThatThrownBy(() -> new JsonFields(json.readTree("{\"authorId\":1}"), () -> failure)
                .only(Set.of("text"))).isSameAs(failure);
        assertThatThrownBy(() -> fields("{\"text\":12}").text("text", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private JsonFields fields(String body) {
        return new JsonFields(json.readTree(body), () -> new IllegalArgumentException("invalid fields"));
    }
}
