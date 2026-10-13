package com.example.taskmanagement.config;

import com.example.taskmanagement.planning.core.GraphValidationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.http.converter.HttpMessageNotReadableException;

/** Shared HTTP representation; pure graph and lifecycle modules remain unaware of HTTP. */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(GraphValidationException.class)
    public ResponseEntity<ProblemDetail> graphProblem(GraphValidationException exception) {
        var status = exception.reason() == GraphValidationException.Reason.CYCLIC_DEPENDENCY
                ? HttpStatus.CONFLICT : HttpStatus.BAD_REQUEST;
        var problem = problem(status, exception.reason().name(), exception.getMessage());
        problem.setProperty("taskIds", exception.taskIds());
        return ResponseEntity.status(status).body(problem);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException exception,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return handleExceptionInternal(exception, problem(status, "VALIDATION_ERROR",
                "Request fields do not satisfy the documented constraints."), headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException exception,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return handleExceptionInternal(exception, problem(status, "MALFORMED_REQUEST",
                "Request body must be valid JSON with the documented field types and dates."),
                headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception exception, Object body,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Object response = body;
        if (!(body instanceof ProblemDetail detail) || detail.getProperties() == null
                || !detail.getProperties().containsKey("code")) {
            var resolved = HttpStatus.resolve(status.value());
            String code = resolved == null ? "HTTP_ERROR" : resolved.name();
            response = problem(status, code, "The request could not be processed.");
        }
        return super.handleExceptionInternal(exception, response, headers, status, request);
    }

    private static ProblemDetail problem(HttpStatusCode status, String code, String detail) {
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("code", code);
        return problem;
    }
}
