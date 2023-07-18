package it.aboutbits.springboot.emailservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@SpringBootApplication
@EnableEmailService
public class TestApplication {
    public static void main(final String[] args) {
        SpringApplication.run(TestApplication.class, args);
    }
}
