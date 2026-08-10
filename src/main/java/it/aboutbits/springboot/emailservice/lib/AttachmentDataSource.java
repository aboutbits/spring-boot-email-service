package it.aboutbits.springboot.emailservice.lib;

import it.aboutbits.springboot.emailservice.lib.exception.AttachmentException;
import org.jspecify.annotations.NullMarked;

import java.io.InputStream;

@NullMarked
public interface AttachmentDataSource {
    InputStream getAttachmentPayload(long fileReference) throws AttachmentException;

    long storeAttachmentPayload(InputStream payload) throws AttachmentException;

    void releaseAttachment(long fileReference) throws AttachmentException;
}
