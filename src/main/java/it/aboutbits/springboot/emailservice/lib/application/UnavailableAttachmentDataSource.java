package it.aboutbits.springboot.emailservice.lib.application;

import it.aboutbits.springboot.emailservice.lib.AttachmentDataSource;
import it.aboutbits.springboot.emailservice.lib.exception.AttachmentException;

import java.io.InputStream;

public final class UnavailableAttachmentDataSource implements AttachmentDataSource {
    @Override
    public InputStream getAttachmentPayload(long fileReference) throws AttachmentException {
        throw new AttachmentException("attachments not available");
    }

    @Override
    public long storeAttachmentPayload(InputStream payload) throws AttachmentException {
        throw new AttachmentException("attachments not available");
    }

    @Override
    public void releaseAttachment(long fileReference) throws AttachmentException {
        throw new AttachmentException("attachments not available");
    }
}
