package com.example.taskmanagement.shared.validation;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE_USE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;
import jakarta.validation.constraints.Pattern;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/** Rejects U+0000 before text reaches persistence; null, blank and length policies remain separate constraints. */
@Documented
@Target({FIELD, METHOD, PARAMETER, ANNOTATION_TYPE, TYPE_USE})
@Retention(RUNTIME)
@Constraint(validatedBy = {})
@Pattern(regexp = NoNul.PATTERN)
@ReportAsSingleViolation
public @interface NoNul {
    String PATTERN = "[^\\x00]*";

    String message() default "must not contain U+0000";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
