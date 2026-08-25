# Spring Boot EMail Service

A reusable mailer service to send emails.

## Setup

Add the mailer service to the classpath by adding the following maven dependency. Versions can be found [here](../../packages)

```xml

<dependency>
    <groupId>it.aboutbits.springboot</groupId>
    <artifactId>emailservice</artifactId>
    <version>x.x.x</version>
</dependency>
```

### Attachments

If you want to use attachments, you will have to create a bean implementing this interface: [AttachmentDataSource.java](src%2Fmain%2Fjava%2Fit%2Faboutbits%2Fspringboot%2Femailservice%2Flib%2FAttachmentDataSource.java)  
This step is optional.

## Usage

### Sending an Email

Use the `ManageEmail` service to schedule an email.

#### Example

```java
// @formatter:off
public void sendMail(final String to,final String subject,final String htmlBody,final String plainTextBody) {
        manageEmail.schedule(
                EmailParameter.builder()
                        .scheduleAt(OffsetDateTime.now())
                        .email(EmailParameter.Email.builder()
                                .fromName(fromName)
                                .fromAddress(fromAddress)
                                .recipient(to)
                                .subject(subject)
                                .textBody(plainTextBody)
                                .htmlBody(htmlBody)
                                .build())
                        .build()
        );
}
// @formatter:on 
```

### Querying Emails

To read email datasets from the database use this class: [QueryEmail.java](src%2Fmain%2Fjava%2Fit%2Faboutbits%2Fspringboot%2Femailservice%2Flib%2Fapplication%2FQueryEmail.java)

### Reporting callback

If you want to receive a report after each run of the scheduler, create a Bean implementing [EmailSchedulerCallback.java](src%2Fmain%2Fjava%2Fit%2Faboutbits%2Fspringboot%2Femailservice%2Flib%2FEmailSchedulerCallback.java)

### Metrics

The library records Micrometer metrics for both schedulers. Micrometer is an optional dependency: if the
application provides a `MeterRegistry` the metrics are recorded, otherwise the library falls back to a no-op
and nothing changes. Nothing has to be enabled in this library.

To scrape them, the application needs `spring-boot-starter-actuator` and `micrometer-registry-prometheus`,
plus the Prometheus endpoint:

```yaml
management:
  endpoints:
    access:
      default: none
    web:
      exposure:
        include:
          - health
          - prometheus
  endpoint:
    health:
      access: read-only
    prometheus:
      access: read-only
```

The following series are exposed:

| Series                                          | Type    | Labels                                                                         | Description                                                                                     |
|-------------------------------------------------|---------|--------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------|
| `app_email_send_duration_seconds`               | timer   | `mode`: `scheduled`, `direct`<br/>`outcome`: `sent`, `retry`, `error`           | One observation per send attempt. `retry` is an attempt that failed but is scheduled for another one, `error` an email that has given up. |
| `app_email_cleanup_duration_seconds`            | timer   | `outcome`: `cleaned`, `error`                                                   | One observation per attachment cleanup attempt.                                                 |
| `app_email_pass_duration_seconds`               | timer   | `job`: `send`, `cleanup`<br/>`status`: `success`, `failed`                      | One observation per scheduler pass. `failed` means the pass itself broke, e.g. the database was unreachable. |
| `app_email_last_run_timestamp_seconds`          | gauge   | `job`: `send`, `cleanup`                                                        | When the scheduler last fired. Stalls if the scheduler is dead or the pod is down.               |
| `app_email_last_success_timestamp_seconds`      | gauge   | `job`: `send`, `cleanup`                                                        | When a pass last got through. Stalls while passes keep failing.                                  |
| `app_email_queue`                               | gauge   | `state`: `pending`, `sending`                                                   | Emails per state, read after each send pass. The terminal states `SENT` and `ERROR` are left out: they only ever grow. Errors are counted by `app_email_send_duration_seconds_count{outcome="error"}`. |
| `app_email_queue_oldest_due_age_seconds`         | gauge   | `state`: `pending`                                                              | How long the oldest email that is already due has been waiting. `0` if nothing is due.           |

Two things to keep in mind when querying them:

- **Aggregate the gauges with `max`, never `sum`.** The queue gauges are read from the database, so every pod
  reports the same numbers - summing them multiplies the backlog by the number of pods.
- The queue gauges are only written by the send scheduler. With
  `aboutbits.emailservice.scheduling.enabled=false` they are never registered, and the series are absent
  rather than zero.

Both timestamp gauges are registered on first use, so a pod that has not completed a pass since starting has
no series at all. This is deliberate: an alert reads an absent series the same way it reads a `NaN` starting
value, while a `0` starting value would look like decades of staleness after every deploy.

The dashboards and alerts themselves belong to the consuming application, since they depend on how it is
deployed. As a starting point, a scheduler that has stopped firing and a backlog that is not being kept up
with read as:

```promql
time() - max by (job) (app_email_last_run_timestamp_seconds) > 300
max(app_email_queue_oldest_due_age_seconds) > 600
```

If the application replaces the metrics sink with its own `EmailMetrics` bean, the library's one steps back;
whatever the application provides is called fail-safe, so a failing sink can never break a send.

### Configuration

To enable this service just add `@EnableEmailService` to your main class. You must also enable `@EnableScheduling` to allow the email queue to be processed.

```java

@SpringBootApplication
@EnableEmailService
@EnableScheduling
public class App {
    public static void main(final String[] args) {
        SpringApplication.run(App.class, args);
    }
}
```

The following configuration options are available:

| Name                                                                    | Default | Description                                                                                                            |
|-------------------------------------------------------------------------|---------|------------------------------------------------------------------------------------------------------------------------|
| `aboutbits.emailservice.migrations.enabled`                             | true    | Enables database migrations.                                                                                           |
| `aboutbits.emailservice.scheduling.enabled`                             | true    | Enables the scheduler sending the emails.                                                                              |
| `aboutbits.emailservice.scheduling.cleanup.enabled`                     | true    | Enables cleanup of attachment files after sending.                                                                     |
| `aboutbits.emailservice.scheduling.interval`                            | 30000   | Milliseconds delay between runs of the scheduler.                                                                      |
| `aboutbits.emailservice.scheduling.stuck-sending-recovery-threshold`    | PT30M   | How long an email may stay in `SENDING` before being considered abandoned (crashed pod) and eligible to be re-claimed. Must comfortably exceed the worst-case SMTP send duration: JavaMail's default connect/read/write timeouts are infinite, so configure `spring.mail.properties.mail.smtp.connectiontimeout`, `spring.mail.properties.mail.smtp.timeout` and `spring.mail.properties.mail.smtp.writetimeout` well below this threshold, otherwise a slow in-flight send can be re-claimed by another pod and delivered twice. |
| `aboutbits.emailservice.scheduling.stuck-cleanup-recovery-threshold`    | PT30M   | How long an email may keep its attachment-cleanup lock before the cleanup is considered abandoned (crashed pod) and eligible to be re-claimed. Must comfortably exceed the worst-case duration of releasing all attachments of a single email, otherwise a slow in-flight cleanup can be re-claimed by another pod and its attachments released twice (harmless only if `AttachmentDataSource.releaseAttachment` is idempotent). |
| `aboutbits.emailservice.scheduling.max-attempts`                        | 3       | Maximum number of send attempts before an email is marked as `ERROR`. Applies only to the scheduled retry loop. Failed attempts are retried with exponential backoff (`scheduling.interval` × 2^attempts); with the defaults a persistently failing email runs attempt 1 → +60s → attempt 2 → +120s → attempt 3 → `ERROR`. |

## Multi-pod deployments

Both schedulers are safe to run on every pod concurrently: the database arbitrates which pod handles each email.
Delivery is at-least-once - if a pod crashes after the SMTP server accepted the message but before the result was persisted, the email may be sent again on recovery.
Attachment Cleanup is at-least-once - if a pod crashes after releasing the attachments but before the result was persisted, the cleanup may be retried on recovery.

## Local development:

To use this library as a local development dependency, you can simply refer to the version `BUILD-SNAPSHOT`.

Check out this repository and run the maven goal `install`. This will build and install this library as version `BUILD-SNAPSHOT` into your local maven cache.

Note that you may have to tell your IDE to reload your main maven project each time you build the library.

## Build & Publish

To build and publish the chart, visit the GitHub Actions page of the repository and trigger the workflow "Release Package" manually.

## Information

About Bits is a company based in South Tyrol, Italy. You can find more information about us on [our website](https://aboutbits.it).

### Support

For support, please contact [info@aboutbits.it](mailto:info@aboutbits.it).

### Credits

- [All Contributors](../../contributors)

### License

The MIT License (MIT). Please see the [license file](license.md) for more information.
