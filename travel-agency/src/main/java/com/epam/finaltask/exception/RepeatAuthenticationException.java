package com.epam.finaltask.exception;

public class RepeatAuthenticationException extends RuntimeException {
    public RepeatAuthenticationException(String message) {
        super(message);
    }
}
