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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

import static it.aboutbits.springboot.emailservice.lib.model.Email.DEFAULT_ENTITY_GRAPH;

@NamedEntityGraph(
        name = "email_service_emails-entity-graph",
        attributeNodes = {
                @NamedAttributeNode("attachments")
        }
)
@Entity
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "email_service_emails")
@NamedEntityGraph(name = DEFAULT_ENTITY_GRAPH, attributeNodes = @NamedAttributeNode("attachments"))
public class Email {
    public static final String DEFAULT_ENTITY_GRAPH = "graph.EmailServiceEmail.default";

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

    @OneToMany(cascade = CascadeType.PERSIST, mappedBy = "email", orphanRemoval = true)
    private Set<EmailAttachment> attachments;

    @Builder.Default
    private boolean attachmentsCleaned = false;

    private OffsetDateTime scheduledAt;
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

    public boolean hasFailed() {
        return EmailState.ERROR.equals(state);
    }
}
