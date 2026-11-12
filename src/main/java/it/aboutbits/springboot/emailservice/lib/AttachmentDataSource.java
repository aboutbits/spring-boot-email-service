package it.aboutbits.springboot.emailservice.lib;

import it.aboutbits.springboot.emailservice.lib.exception.AttachmentException;
import org.jspecify.annotations.NullMarked;

import java.io.InputStream;

/**
 * Storage for attachment payloads.
 * <p>
 * {@link #storeAttachmentPayload} runs inside the caller's transaction of {@code schedule} and {@code sendAsync}.
 * The default JDBC implementation joins that transaction, so payloads commit and roll back together with the
 * email. An implementation backed by an external store (files, S3, ...) does not: it must either take part in the
 * Spring transaction itself, e.g. by releasing the payload again from a rollback synchronization, or tolerate
 * orphaned payloads of rolled-back emails.
 */
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
