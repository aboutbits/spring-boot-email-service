package it.aboutbits.springboot.emailservice.lib;

import it.aboutbits.springboot.emailservice.lib.model.Email;

public record EmailAttachmentDto(
        long id,

        Email email,

        String fileName,

        String contentType,

        String reference
) {
}
