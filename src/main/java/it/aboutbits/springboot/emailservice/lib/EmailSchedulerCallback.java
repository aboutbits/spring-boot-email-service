package it.aboutbits.springboot.emailservice.lib;

public interface EmailSchedulerCallback {
    void report(Report report);

    record Report(
            int total,
            int sent,
            int errors
    ) {
    }
}
