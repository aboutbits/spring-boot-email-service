package it.aboutbits.springboot.emailservice.lib.application;

import org.jspecify.annotations.NullMarked;

// Published by ManageEmail.sendAsync inside the caller's transaction and consumed by SendAsyncEmails after the commit.
// When the async dispatch is disabled, nobody listens and the scheduler sends the email instead.
@NullMarked
record AsyncEmailQueued(long emailId) {
}
