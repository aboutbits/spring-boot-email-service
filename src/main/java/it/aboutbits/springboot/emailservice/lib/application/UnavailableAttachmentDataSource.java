package it.aboutbits.springboot.emailservice.lib.application;

import it.aboutbits.springboot.emailservice.lib.AttachmentDataSource;

import java.io.InputStream;

public final class UnavailableAttachmentDataSource implements AttachmentDataSource {
    @Override
    public InputStream getAttachmentPayload(final String reference) {
        throw new RuntimeException("attachments not available");
    }

    @Override
    public void releaseAttachment(final String reference) {
        throw new RuntimeException("attachments not available");
    }
}
