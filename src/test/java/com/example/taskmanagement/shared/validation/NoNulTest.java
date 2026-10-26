package com.example.taskmanagement.shared.validation;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class NoNulTest {
    private static ValidatorFactory factory;
    private static Validator validator;

    record Text(@NoNul String value) {
    }

    @BeforeAll
    static void validator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void close() {
        factory.close();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", "Привіт 😀", "line one\r\n\tline two", "<script>plain text</script>", "\1"})
    void preservesOptionalNullWhitespaceUnicodeAndPlainText(String text) {
        assertThat(validator.validate(new Text(text))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"\0", "\0text", "text\0", "before\0after", "line\n\0next"})
    void rejectsNulAtEveryPosition(String text) {
        assertThat(validator.validate(new Text(text))).singleElement()
                .satisfies(violation -> assertThat(violation.getConstraintDescriptor().getAnnotation())
                        .isInstanceOf(NoNul.class));
    }
}
