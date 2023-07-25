package it.aboutbits.springboot.emailservice.lib.application;


import it.aboutbits.springboot.emailservice.lib.EmailDto;
import it.aboutbits.springboot.emailservice.lib.EmailState;
import it.aboutbits.springboot.emailservice.lib.jpa.EmailRepository;
import it.aboutbits.springboot.emailservice.lib.model.Email;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
public class QueryEmail {
    private final EmailRepository emailRepository;
    private final EmailMapper emailMapper;
    private final EntityManager entityManager;

    public Page<EmailDto> paginatedByState(EmailState state, PageRequest pageParameter) {
        var pageRequest = PageRequest.of(pageParameter.getPageNumber(), pageParameter.getPageSize(), Sort.by("updatedAt"));

        return emailMapper.toDto(emailRepository.findByState(state, pageRequest));
    }

    public List<EmailDto> byIds(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Collections.emptyList();
        }
        return emailMapper.toDto(emailRepository.findByIdIn(ids));
    }

    List<Email> readyToSend() {
        var entityGraph = entityManager.getEntityGraph("email_service_emails-entity-graph");
        return entityManager.createQuery("""
                        SELECT e from Email e WHERE e.sendingScheduledAt < :scheduledBefore AND e.state IN (
                            it.aboutbits.springboot.emailservice.lib.EmailState.PENDING,
                            it.aboutbits.springboot.emailservice.lib.EmailState.ERROR
                        )
                        """, Email.class)
                .setParameter("scheduledBefore", OffsetDateTime.now())
                .setHint("jakarta.persistence.fetchgraph", entityGraph)
                .getResultList();
    }

    public Optional<EmailDto> byId(long id) {
        return emailRepository.findById(id).map(emailMapper::toDto);
    }
}
