package it.aboutbits.springboot.emailservice.lib.application;

import it.aboutbits.springboot.emailservice.lib.EmailDto;
import it.aboutbits.springboot.emailservice.lib.EmailState;
import it.aboutbits.springboot.emailservice.lib.jpa.EmailRepository;
import it.aboutbits.springboot.emailservice.support.database.WithPostgres;
import it.aboutbits.springboot.emailservice.support.database.factory.EmailFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageRequest;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;


@SpringBootTest
@WithPostgres
class QueryEmailTest {
    @MockBean
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
    void givenEmailNotificationWithPagination_byReference_shouldSuccess() {
        emailRepository.saveAll(Set.of(
                EmailFactory.once().reference("something").build(),
                EmailFactory.once().reference("something else").build(),
                EmailFactory.once().reference("something").build()
        ));

        var result = queryEmail.byReference(
                "something"
        );

        assertThat(result).hasSize(2);
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
}
