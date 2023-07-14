package it.aboutbits.springboot.emailservice.lib.application;

import it.aboutbits.springboot.emailservice.lib.AttachmentDataSource;
import it.aboutbits.springboot.emailservice.lib.exception.AttachmentException;

import java.io.InputStream;

public final class UnavailableAttachmentDataSource implements AttachmentDataSource {
    @Override
    public InputStream getAttachmentPayload(final String reference) throws AttachmentException {
        throw new AttachmentException("attachments not available");
    }

    @Override
    public void releaseAttachment(final String reference) throws AttachmentException {
        throw new AttachmentException("attachments not available");
    }
}
