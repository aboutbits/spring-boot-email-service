package it.aboutbits.springboot.emailservice.lib;

import java.io.InputStream;

public interface AttachmentDataSource {
    InputStream getAttachmentPayload(String reference);

    void releaseAttachment(String reference);
}
