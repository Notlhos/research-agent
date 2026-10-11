package io.github.notlhos.research.application.exception;

/**
 * Machine-readable codes returned in {@link ErrorResponse#codes()}. A validation error is followed by one
 * {@code <field>.<constraint>} code per invalid field, for example {@code topic.Size}.
 */
public final class ErrorCode {

    /* ***** General Errors ***** */

    public static final String VALIDATION_FAILED = "VALIDATION_FAILED";

    public static final String MALFORMED_REQUEST = "MALFORMED_REQUEST";

    public static final String UNAUTHORIZED = "UNAUTHORIZED";

    public static final String FORBIDDEN = "FORBIDDEN";

    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";

    /* ***** Research Run Errors ***** */

    public static final String RUN_NOT_FOUND = "RESEARCH_RUN_NOT_FOUND";

    public static final String RUN_NOT_CANCELLABLE = "RESEARCH_RUN_NOT_CANCELLABLE";

    public static final String RUN_NOT_RETRYABLE = "RESEARCH_RUN_NOT_RETRYABLE";

    private ErrorCode() {
    }

}
