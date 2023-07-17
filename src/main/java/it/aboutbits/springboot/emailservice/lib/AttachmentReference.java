package it.aboutbits.springboot.emailservice.lib;

import jakarta.validation.constraints.NotBlank;
import lombok.NonNull;

public record AttachmentReference(
        @NonNull
        @NotBlank
        String value
) {
}
