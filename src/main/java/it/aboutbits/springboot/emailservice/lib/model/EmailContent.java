package it.aboutbits.springboot.emailservice.lib.model;

import jakarta.persistence.Embeddable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;

@Embeddable
@NullMarked
public record EmailContent(
        String subject,

        String fromAddress,
        String fromName,

        @Nullable
        String replyToAddress,
        @Nullable
        String replyToName,

        @JdbcTypeCode(SqlTypes.JSON)
        List<String> recipients,

        String textBody,
        String htmlBody
) {
}
