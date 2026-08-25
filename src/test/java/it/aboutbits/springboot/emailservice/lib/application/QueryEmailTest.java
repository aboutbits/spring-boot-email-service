package it.aboutbits.springboot.emailservice.lib.application;

import it.aboutbits.springboot.emailservice.lib.EmailDto;
import it.aboutbits.springboot.emailservice.lib.EmailState;
import it.aboutbits.springboot.emailservice.lib.jpa.EmailRepository;
import it.aboutbits.springboot.emailservice.support.database.WithPostgres;
import it.aboutbits.springboot.emailservice.support.database.factory.EmailFactory;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;


@SpringBootTest
@WithPostgres
@NullMarked
class QueryEmailTest {
    @MockitoBean
    JavaMailSender javaMailSender;

    @Autowired
    EmailRepository emailRepository;

    @Autowired
    QueryEmail queryEmail;

    @Test
    void givenEmailNotification_byState_shouldSuccess() {
        emailRepository.saveAll(Set.of(
                EmailFactory.once().build(),
                EmailFactory.once().state(EmailState.SENT).build(),
                EmailFactory.once().state(EmailState.SENT).build()
        ));

        var result = queryEmail.paginatedByState(
                EmailState.SENT,
                PageRequest.of(0, 20)
        );

        assertThat(result.getTotalElements()).isEqualTo(2L);
    }

    @Test
    void givenEmailNotificationWithPagination_byState_shouldSuccess() {
        emailRepository.saveAll(Set.of(
                EmailFactory.once().build(),
                EmailFactory.once().state(EmailState.SENT).build(),
                EmailFactory.once().state(EmailState.SENT).build()
        ));

        var result = queryEmail.paginatedByState(
                EmailState.SENT,
                PageRequest.of(1, 1)
        );

        assertThat(result.getTotalPages()).isEqualTo(2);
        assertThat(result.getTotalElements()).isEqualTo(2L);
        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    void givenEmailNotification_byIds_shouldSuccess() {
        var notificationA = emailRepository.save(EmailFactory.once().build());
        var notificationB = emailRepository.save(EmailFactory.once().build());
        emailRepository.save(EmailFactory.once().build());

        var result = queryEmail.byIds(Set.of(notificationA.getId(), notificationB.getId()));

        assertThat(result)
                .hasSize(2)
                .map(EmailDto::id)
                .containsExactlyInAnyOrder(notificationA.getId(), notificationB.getId());

    }

    @Test
    void givenEmailNotification_byIdOrFail_shouldSuccess() {
        var notification = emailRepository.save(EmailFactory.once().build());

        var result = queryEmail.byId(notification.getId());

        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo(notification.getId());
    }

    @Test
    void givenNoEmailNotification_byIdOrFail_shouldFail() {
        assertThat(queryEmail.byId(123L)).isNotPresent();
    }

    @Test
    void givenEmailsInEveryState_queueSnapshot_shouldCountOnlyTheOpenOnesAndAgeTheOldestDueOne() {
        emailRepository.saveAll(Set.of(
                EmailFactory.once().scheduledAt(OffsetDateTime.now().minus(10, ChronoUnit.MINUTES)).build(),
                EmailFactory.once().scheduledAt(OffsetDateTime.now().minus(5, ChronoUnit.MINUTES)).build(),
                EmailFactory.once().scheduledAt(OffsetDateTime.now().plus(1, ChronoUnit.HOURS)).build(),
                EmailFactory.once().state(EmailState.SENDING).build(),
                EmailFactory.once().state(EmailState.SENT).build(),
                EmailFactory.once().state(EmailState.ERROR).build()
        ));

        var result = queryEmail.queueSnapshot();

        assertThat(result.pending()).isEqualTo(3L);
        assertThat(result.sending()).isEqualTo(1L);
        assertThat(result.oldestDueAge()).isBetween(Duration.ofMinutes(9), Duration.ofMinutes(11));
    }

    @Test
    void givenNothingIsDue_queueSnapshot_shouldReportNoAge() {
        emailRepository.save(EmailFactory.once().scheduledAt(OffsetDateTime.now().plus(1, ChronoUnit.HOURS)).build());

        var result = queryEmail.queueSnapshot();

        assertThat(result.pending()).isEqualTo(1L);
        assertThat(result.oldestDueAge()).isNull();
    }
}
