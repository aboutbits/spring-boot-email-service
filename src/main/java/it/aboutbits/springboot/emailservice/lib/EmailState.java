package it.aboutbits.springboot.emailservice.lib;

import org.jspecify.annotations.NullMarked;

@NullMarked
public enum EmailState {
    PENDING,
    SENDING,
    SENT,
    ERROR
}
