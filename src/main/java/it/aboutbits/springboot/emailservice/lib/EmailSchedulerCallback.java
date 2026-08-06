package it.aboutbits.springboot.emailservice.lib;

import org.jspecify.annotations.NullMarked;

@NullMarked
public interface EmailSchedulerCallback {
    void report(Report report);

    record Report(
            int total,
            int sent,
            int errors
    ) {
    }
}
