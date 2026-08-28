package it.aboutbits.springboot.emailservice.lib;

import it.aboutbits.springboot.emailservice.lib.exception.AttachmentException;
import org.jspecify.annotations.NullMarked;

import java.io.InputStream;

@NullMarked
public interface AttachmentDataSource {
    InputStream getAttachmentPayload(long fileReference) throws AttachmentException;

    /**
     * Stores the payload and returns the reference used to read it back later.
     * The implementation takes ownership of the stream and must close it.
     *
     * @param payload the attachment payload, closed by the implementation
     * @return the reference to pass to {@link #getAttachmentPayload(long)}
     */
    long storeAttachmentPayload(InputStream payload) throws AttachmentException;

    void releaseAttachment(long fileReference) throws AttachmentException;
}
