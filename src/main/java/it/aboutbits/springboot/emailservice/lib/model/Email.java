package it.aboutbits.springboot.emailservice.lib.model;

import it.aboutbits.springboot.emailservice.lib.EmailState;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.NamedAttributeNode;
import jakarta.persistence.NamedEntityGraph;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

@NamedEntityGraph(
        name = "email_service_emails-entity-graph",
        attributeNodes = {
                @NamedAttributeNode("recipients")
        }
)
@Entity
@Getter
@Setter
@Table(name = "email_service_emails")
public class Email {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private EmailState state;

    private String subject;

    private String fromAddress;
    private String fromName;

    @JdbcTypeCode(SqlTypes.JSON)
    private List<String> recipients;

    private String textBody;
    private String htmlBody;

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "email", orphanRemoval = true)
    private Set<EmailAttachment> attachments;

    private String reference;

    private OffsetDateTime sendingScheduledAt;
    private OffsetDateTime sentAt;

    private OffsetDateTime errorAt;
    private String errorMessage;

    @CreationTimestamp
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    private OffsetDateTime updatedAt;

    public boolean isSent() {
        return EmailState.SENT.equals(state);
    }
}
