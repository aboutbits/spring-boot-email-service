package it.aboutbits.springboot.emailservice.support.database.factory;

import it.aboutbits.springboot.emailservice.lib.EmailState;
import it.aboutbits.springboot.emailservice.lib.model.Email;
import it.aboutbits.springboot.emailservice.lib.model.EmailContent;
import it.aboutbits.springboot.testing.testdata.FakerExtended;
import org.jspecify.annotations.NullMarked;

import java.time.OffsetDateTime;
import java.util.List;

@NullMarked
public final class EmailFactory {
    private static final FakerExtended FAKER = new FakerExtended();

    private EmailFactory() {
    }

    public static Email.EmailBuilder once() {
        var body = FAKER.lorem().paragraph();

        return Email.builder()
                .state(EmailState.PENDING)
                .scheduledAt(OffsetDateTime.now())
                .content(new EmailContent(
                        "Email subject",
                        FAKER.internet().emailAddress(),
                        FAKER.name().fullName(),
                        FAKER.internet().emailAddress(),
                        FAKER.name().fullName(),
                        List.of(FAKER.internet().emailAddress(), FAKER.internet().emailAddress()),
                        body,
                        "<h1>" + body + "</h1>"
                ));
    }
}
