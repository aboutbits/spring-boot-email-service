package it.aboutbits.springboot.emailservice.lib.exception;

public class AttachmentException extends Exception {
    public AttachmentException() {
        super();
    }

    public AttachmentException(final String message) {
        super(message);
    }

    public AttachmentException(final String message, final Throwable cause) {
        super(message, cause);
    }

    public AttachmentException(final Throwable cause) {
        super(cause);
    }

    protected AttachmentException(final String message, final Throwable cause, final boolean enableSuppression, final boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
