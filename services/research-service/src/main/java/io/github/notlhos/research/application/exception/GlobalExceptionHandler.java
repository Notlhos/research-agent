package io.github.notlhos.research.application.exception;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ErrorResponse> handleApiException(ApiException exception) {
        log.info("Request rejected with {}: {}", exception.getCode(), exception.getMessage());
        return respond(exception.getStatus(), exception.getMessage(), List.of(exception.getCode()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
        List<FieldError> errors = exception.getBindingResult().getFieldErrors().stream()
                .sorted(Comparator.comparing(FieldError::getField))
                .toList();
        String details = errors.stream()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        List<String> codes = Stream.concat(
                        Stream.of(ErrorCode.VALIDATION_FAILED),
                        errors.stream().map(error -> error.getField() + "." + error.getCode()))
                .toList();
        return respond(HttpStatus.BAD_REQUEST, "Validation failed: " + details, codes);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException exception) {
        return respond(HttpStatus.BAD_REQUEST, "Malformed request body", List.of(ErrorCode.MALFORMED_REQUEST));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        return respond(HttpStatus.BAD_REQUEST, "Invalid value for parameter " + exception.getName(),
                List.of(ErrorCode.MALFORMED_REQUEST));
    }

    @ExceptionHandler(PropertyReferenceException.class)
    ResponseEntity<ErrorResponse> handleUnknownSortProperty(PropertyReferenceException exception) {
        return respond(HttpStatus.BAD_REQUEST, "Unknown sort property " + exception.getPropertyName(),
                List.of(ErrorCode.MALFORMED_REQUEST));
    }

    @ExceptionHandler({
            ErrorResponseException.class,
            HttpMediaTypeNotAcceptableException.class,
            HttpMediaTypeNotSupportedException.class,
            HttpRequestMethodNotSupportedException.class,
            MissingServletRequestParameterException.class,
            NoResourceFoundException.class})
    ResponseEntity<ErrorResponse> handleFrameworkException(Exception exception) {
        org.springframework.web.ErrorResponse error = (org.springframework.web.ErrorResponse) exception;
        HttpStatus status = HttpStatus.valueOf(error.getStatusCode().value());
        return respond(status, status.getReasonPhrase(), List.of(status.name()));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> handleUnexpected(Exception exception) {
        log.error("Unexpected error while handling request", exception);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error", List.of(ErrorCode.INTERNAL_ERROR));
    }

    /* ***** Private Methods ***** */

    private static ResponseEntity<ErrorResponse> respond(HttpStatus status, String message, List<String> codes) {
        return ResponseEntity.status(status).body(ErrorResponse.of(status, message, codes));
    }

}
