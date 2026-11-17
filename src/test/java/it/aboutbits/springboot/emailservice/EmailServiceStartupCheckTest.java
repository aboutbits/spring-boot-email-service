package it.aboutbits.springboot.emailservice;

import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
@NullMarked
class EmailServiceStartupCheckTest {
    @Test
    void givenSchedulerWithoutEnableScheduling_shouldLogError(CapturedOutput output) {
        EmailServiceStartupCheck.check(true, true, true, false);

        assertThat(output).contains("ERROR").contains("@EnableScheduling is missing");
    }

    @Test
    void givenCleanupOnlyWithoutEnableScheduling_shouldLogError(CapturedOutput output) {
        EmailServiceStartupCheck.check(false, true, false, false);

        assertThat(output).contains("ERROR").contains("@EnableScheduling is missing");
    }

    @Test
    void givenAsyncDispatchWithoutScheduler_shouldLogWarning(CapturedOutput output) {
        EmailServiceStartupCheck.check(false, false, true, true);

        assertThat(output).contains("WARN").contains("scheduler is disabled on this pod");
        assertThat(output).doesNotContain("ERROR");
    }

    @Test
    void givenSchedulerAndEnableScheduling_shouldStaySilent(CapturedOutput output) {
        EmailServiceStartupCheck.check(true, true, true, true);

        assertThat(output).doesNotContain("EmailService:");
    }

    @Test
    void givenEverythingDisabled_shouldStaySilent(CapturedOutput output) {
        EmailServiceStartupCheck.check(false, false, false, false);

        assertThat(output).doesNotContain("EmailService:");
    }
}
