package it.aboutbits.springboot.emailservice.lib;

import it.aboutbits.springboot.emailservice.lib.exception.AttachmentException;

import java.io.InputStream;

public interface AttachmentDataSource {
    InputStream getAttachmentPayload(String reference) throws AttachmentException;

    void releaseAttachment(String reference) throws AttachmentException;
}
