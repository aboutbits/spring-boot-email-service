package it.aboutbits.springboot.emailservice.lib.application;


import it.aboutbits.springboot.emailservice.lib.EmailDto;
import it.aboutbits.springboot.emailservice.lib.EmailState;
import it.aboutbits.springboot.emailservice.lib.jpa.EmailRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@NullMarked
public class QueryEmail {
    // Hibernate convention for "jakarta.persistence.lock.timeout":
    // -2 translates to "SKIP LOCKED" at the database layer (matches "org.hibernate.Timeouts.SKIP_LOCKED_MILLI")
    private static final int SKIP_LOCKED_TIMEOUT = -2;
    private static final String LOCK_TIMEOUT_HINT = "jakarta.persistence.lock.timeout";
    private final EmailRepository emailRepository;
    private final EmailMapper emailMapper;
    private final EntityManager entityManager;

    public Page<EmailDto> paginatedByState(EmailState state, PageRequest pageParameter) {
        var pageRequest = PageRequest.of(
                pageParameter.getPageNumber(),
                pageParameter.getPageSize(),
                Sort.by("updatedAt")
        );

        return emailMapper.toDto(emailRepository.findByState(state, pageRequest));
    }

    public List<EmailDto> byIds(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Collections.emptyList();
        }
        return emailMapper.toDto(emailRepository.findByIdIn(ids));
    }

    List<Long> claimReadyToSendIds(int limit) {
        return entityManager.createQuery(
                        """
                                select e.id from Email e where e.scheduledAt < :scheduledBefore and e.state in (
                                    it.aboutbits.springboot.emailservice.lib.EmailState.PENDING,
                                    it.aboutbits.springboot.emailservice.lib.EmailState.ERROR
                                )
                                order by e.scheduledAt
                                """, Long.class
                )
                .setParameter("scheduledBefore", OffsetDateTime.now())
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .setHint(LOCK_TIMEOUT_HINT, SKIP_LOCKED_TIMEOUT)
                .setMaxResults(limit)
                .getResultList();
    }

    List<Long> claimReadyToCleanupIds(int limit) {
        return entityManager.createQuery(
                        """
                                select e.id from Email e where e.attachmentsCleaned=false and e.state=it.aboutbits.springboot.emailservice.lib.EmailState.SENT
                                order by e.updatedAt
                                """, Long.class
                )
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .setHint(LOCK_TIMEOUT_HINT, SKIP_LOCKED_TIMEOUT)
                .setMaxResults(limit)
                .getResultList();
    }

    public Optional<EmailDto> byId(long id) {
        return emailRepository.findById(id).map(emailMapper::toDto);
    }
}
