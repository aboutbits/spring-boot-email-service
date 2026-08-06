package it.aboutbits.springboot.emailservice.lib.exception;

import org.jspecify.annotations.NullMarked;

@NullMarked
public class AttachmentException extends Exception {
    public AttachmentException() {
        super();
    }

    public AttachmentException(String message) {
        super(message);
    }

    public AttachmentException(String message, Throwable cause) {
        super(message, cause);
    }

    public AttachmentException(Throwable cause) {
        super(cause);
    }

    protected AttachmentException(
            String message,
            Throwable cause,
            boolean enableSuppression,
            boolean writableStackTrace
    ) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
