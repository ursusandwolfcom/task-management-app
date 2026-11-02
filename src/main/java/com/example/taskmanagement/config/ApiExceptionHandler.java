package com.example.taskmanagement.config;

import com.example.taskmanagement.attachment.api.AttachmentException;
import com.example.taskmanagement.collaboration.api.CollaborationException;
import com.example.taskmanagement.notification.api.NotificationException;
import com.example.taskmanagement.planning.core.GraphValidationException;
import com.example.taskmanagement.identity.api.IdentityException;
import com.example.taskmanagement.project.api.ProjectException;
import com.example.taskmanagement.task.api.TaskException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
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

    @ExceptionHandler(NotificationException.class)
    public ResponseEntity<ProblemDetail> notificationProblem(NotificationException exception) {
        HttpStatus status = switch (exception.reason()) {
            case NOTIFICATION_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case VALIDATION_ERROR -> HttpStatus.BAD_REQUEST;
        };
        return uncachedProblem(status, exception.reason().name(),
                "The notification operation could not be completed.");
    }

    @ExceptionHandler(AttachmentException.class)
    public ResponseEntity<ProblemDetail> attachmentProblem(AttachmentException exception) {
        HttpStatus status = switch (exception.reason()) {
            case ATTACHMENT_NOT_FOUND, TASK_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case VALIDATION_ERROR -> HttpStatus.BAD_REQUEST;
            case STORAGE_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        return uncachedProblem(status, exception.reason().name(),
                "The attachment operation could not be completed.");
    }

    @ExceptionHandler(CollaborationException.class)
    public ResponseEntity<ProblemDetail> collaborationProblem(CollaborationException exception) {
        HttpStatus status = switch (exception.reason()) {
            case COMMENT_NOT_FOUND, TASK_NOT_FOUND, LABEL_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case VALIDATION_ERROR -> HttpStatus.BAD_REQUEST;
            case LABEL_ALREADY_EXISTS, VERSION_CONFLICT, CROSS_PROJECT_LABEL -> HttpStatus.CONFLICT;
        };
        return uncachedProblem(status, exception.reason().name(),
                "The collaboration operation could not be completed.");
    }

    @ExceptionHandler(ProjectException.class)
    public ResponseEntity<ProblemDetail> projectProblem(ProjectException exception) {
        HttpStatus status = switch (exception.reason()) {
            case PROJECT_NOT_FOUND, MEMBER_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case VALIDATION_ERROR -> HttpStatus.BAD_REQUEST;
            case VERSION_CONFLICT, MEMBER_ALREADY_EXISTS, OWNER_REMOVAL_FORBIDDEN,
                    MEMBER_HAS_ASSIGNMENTS, PROJECT_NOT_EMPTY -> HttpStatus.CONFLICT;
        };
        return uncachedProblem(status, exception.reason().name(), "The project operation could not be completed.");
    }

    @ExceptionHandler(TaskException.class)
    public ResponseEntity<ProblemDetail> taskProblem(TaskException exception) {
        HttpStatus status = switch (exception.reason()) {
            case TASK_NOT_FOUND, DEPENDENCY_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case VALIDATION_ERROR -> HttpStatus.BAD_REQUEST;
            default -> HttpStatus.CONFLICT;
        };
        return uncachedProblem(status, exception.reason().name(), "The task operation could not be completed.");
    }

    @ExceptionHandler(IdentityException.class)
    public ResponseEntity<ProblemDetail> identityProblem(IdentityException exception) {
        HttpStatus status = switch (exception.reason()) {
            case INVALID_CREDENTIALS, INVALID_SESSION -> HttpStatus.UNAUTHORIZED;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case USER_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case DUPLICATE_IDENTITY, VERSION_CONFLICT, ADMIN_ALREADY_EXISTS, LAST_ADMIN -> HttpStatus.CONFLICT;
        };
        return uncachedProblem(status, exception.reason().name(), "The identity operation could not be completed.");
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> invalidIdentityCommand(ConstraintViolationException exception) {
        return uncachedProblem(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Request fields are invalid.");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> identityConstraint(DataIntegrityViolationException exception) {
        String diagnostic = exception.getMostSpecificCause().getMessage();
        boolean duplicate = diagnostic != null && (diagnostic.contains("users_username_unique")
                || diagnostic.contains("users_email_unique"));
        HttpStatus status = duplicate ? HttpStatus.CONFLICT : HttpStatus.INTERNAL_SERVER_ERROR;
        return uncachedProblem(status, duplicate ? "DUPLICATE_IDENTITY" : "INTERNAL_ERROR",
                "The operation could not be completed.");
    }

    @ExceptionHandler(GraphValidationException.class)
    public ResponseEntity<ProblemDetail> graphProblem(GraphValidationException exception) {
        var status = exception.reason() == GraphValidationException.Reason.CYCLIC_DEPENDENCY
                ? HttpStatus.CONFLICT : HttpStatus.BAD_REQUEST;
        var problem = problem(status, exception.reason().name(), exception.getMessage());
        problem.setProperty("taskIds", exception.taskIds());
        return ResponseEntity.status(status).header("Cache-Control", "no-store").body(problem);
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
    protected ResponseEntity<Object> handleMaxUploadSizeExceededException(
            org.springframework.web.multipart.MaxUploadSizeExceededException exception, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        return handleExceptionInternal(exception, problem(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                "The attachment operation could not be completed."), headers, HttpStatus.BAD_REQUEST, request);
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

    private static ResponseEntity<ProblemDetail> uncachedProblem(HttpStatusCode status, String code, String detail) {
        return ResponseEntity.status(status).header("Cache-Control", "no-store").body(problem(status, code, detail));
    }

    private static ProblemDetail problem(HttpStatusCode status, String code, String detail) {
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("code", code);
        return problem;
    }
}
