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
| `aboutbits.emailservice.scheduling.stuck-sending-recovery-threshold`    | PT5M    | How long an email may stay in `SENDING` before being considered abandoned (crashed pod) and eligible to be re-claimed. |
| `aboutbits.emailservice.scheduling.max-attempts`                        | 3       | Maximum number of send attempts before an email is marked as `ERROR`. Applies only to the scheduled retry loop.        |

## Multi-pod deployments

The scheduler is safe to run on every pod concurrently. Each pass performs
three independent steps:

1. **Candidate scan** — a plain `SELECT` returns up to `batch-size` ids of
   emails whose `scheduled_at` has passed and whose state is `PENDING` or `SENDING`
   older than `stuck-sending-recovery-threshold` (crashed-pod recovery). `ERROR`
   is terminal: rows that exhausted `max-attempts` are never re-picked
   automatically — an operator can reset them to `PENDING` if a retry is desired.
   Two pods may see overlapping ids at this step, and that's fine — the atomic
   claim below arbitrates.
2. **Atomic claim** — for each candidate id, a single compare-and-set
   `UPDATE … SET state = SENDING … WHERE id = ? AND state IN (PENDING, ERROR, staleSENDING)`
   is issued. The database serializes concurrent updates against the same row so
   exactly one pod sees `rowsAffected = 1` and owns that email; the other pods
   see `0` and move on.
3. **Send and persist** — the winning pod calls SMTP outside any database
   transaction and then writes the final `SENT` / `ERROR` / `PENDING` state through a small `save()`.

Crash recovery: if a pod dies between "claim" and "persist result", the row
stays in `SENDING` with its `execution_start_time` frozen. After
`stuck-sending-recovery-threshold` any pod's next pass picks it up as a
candidate again and retries. Repeated crashes therefore count against
`max-attempts` and eventually escalate the row to `ERROR` rather than looping forever.

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
