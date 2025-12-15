package com.slack.exception;

/**
 * Exception thrown when sequence number generation fails
 */
public class SequenceGenerationException extends RuntimeException {
    public SequenceGenerationException(String message) {
        super(message);
    }

    public SequenceGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
