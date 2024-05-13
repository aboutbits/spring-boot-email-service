package it.aboutbits.springboot.emailservice.support.database.factory;

import com.github.javafaker.Faker;
import it.aboutbits.springboot.emailservice.lib.EmailState;
import it.aboutbits.springboot.emailservice.lib.model.Email;

import java.time.OffsetDateTime;
import java.util.List;

public final class EmailFactory {
    private static final Faker FAKER = new Faker();

    private EmailFactory() {
    }

    public static Email.EmailBuilder once() {
        var body = FAKER.lorem().paragraph();

        return Email.builder()
                .state(EmailState.PENDING)
                .subject("Email subject")
                .textBody(body)
                .htmlBody("<h1>" + body + "</h1>")
                .scheduledAt(OffsetDateTime.now())
                .fromAddress(FAKER.internet().emailAddress())
                .fromName(FAKER.name().fullName())
                .replyToAddress(FAKER.internet().emailAddress())
                .replyToName(FAKER.name().fullName())
                .recipients(List.of(FAKER.internet().emailAddress(), FAKER.internet().emailAddress()));
    }
}
