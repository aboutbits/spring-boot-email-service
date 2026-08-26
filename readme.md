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

#### Inline (CID) attachments

To embed an attachment inline, set a `contentId` on the attachment and reference it in the `htmlBody` via the `cid:` scheme.
This is the equivalent of `MimeMessageHelper.addInline(...)` and renders in all major email clients.

```java
// @formatter:off
EmailParameter.Email.builder()
        // ...
        .htmlBody("<img src=\"cid:header-logo\"><h1>Hello!</h1>")
        .attachment(EmailParameter.Email.Attachment.builder()
                .contentId("header-logo")
                .fileName("logo.png")
                .contentType("image/png")
                .payload(new ClassPathResource("/templates/mail/images/logo.png").getInputStream())
                .build())
        .build();
// @formatter:on
```

Attachments without a `contentId` are added as regular attachments. Each `contentId` must be unique and referenced
in the `htmlBody` as `cid:contentId`, otherwise validation fails.

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
