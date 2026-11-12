package it.aboutbits.springboot.emailservice;

import org.jspecify.annotations.NullMarked;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@SpringBootApplication
@EnableEmailService
// Required by the library (see readme). The test profile sets a one-hour scheduler interval, so the scheduled
// methods never fire during the suite and tests call them explicitly instead.
@EnableScheduling
@NullMarked
public class TestApplication {
    public static void main(final String[] args) {
        SpringApplication.run(TestApplication.class, args);
    }
}
