package it.aboutbits.springboot.emailservice.lib;

import it.aboutbits.springboot.emailservice.lib.exception.AttachmentException;

import java.io.InputStream;

public interface AttachmentDataSource {
    InputStream getAttachmentPayload(AttachmentReference reference) throws AttachmentException;

    AttachmentReference storeAttachmentPayload(InputStream payload) throws AttachmentException;

    void releaseAttachment(AttachmentReference reference) throws AttachmentException;
}
