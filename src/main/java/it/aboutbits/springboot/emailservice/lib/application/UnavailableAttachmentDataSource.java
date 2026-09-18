package it.aboutbits.springboot.emailservice.lib.application;

import it.aboutbits.springboot.emailservice.lib.AttachmentDataSource;
import it.aboutbits.springboot.emailservice.lib.exception.AttachmentException;
import org.jspecify.annotations.NullMarked;

import java.io.IOException;
import java.io.InputStream;

@NullMarked
public final class UnavailableAttachmentDataSource implements AttachmentDataSource {
    @Override
    public InputStream getAttachmentPayload(long fileReference) throws AttachmentException {
        throw new AttachmentException("attachments not available");
    }

    @Override
    public long storeAttachmentPayload(InputStream payload) throws AttachmentException {
        try (payload) {
            throw new AttachmentException("attachments not available");
        } catch (IOException e) {
            throw new AttachmentException("attachments not available", e);
        }
    }

    @Override
    public void releaseAttachment(long fileReference) throws AttachmentException {
        throw new AttachmentException("attachments not available");
    }
}
