package it.aboutbits.springboot.emailservice.lib;

import it.aboutbits.springboot.emailservice.lib.model.Email;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public record EmailAttachmentDto(
        long id,

        Email email,

        String fileName,

        String contentType,

        @Nullable
        String contentId,

        long fileReference
) {
}
