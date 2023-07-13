package it.aboutbits.springboot.emailservice.lib.jpa;


import it.aboutbits.springboot.emailservice.lib.EmailState;
import it.aboutbits.springboot.emailservice.lib.model.Email;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;

public interface EmailRepository extends JpaRepository<Email, Long> {
    Page<Email> findByState(EmailState state, PageRequest pageRequest);

    @Query("""
            SELECT e from Email e WHERE e.sendingScheduledAt < :scheduledBefore AND e.state IN (
                it.aboutbits.springboot.emailservice.lib.EmailState.PENDING,
                it.aboutbits.springboot.emailservice.lib.EmailState.ERROR
            )
            """)
    List<Email> findReadyToSend(OffsetDateTime scheduledBefore);

    List<Email> findByIdIn(Collection<Long> ids);
}
