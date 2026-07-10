package com.urlshortener.exception;

public class PasswordRequiredException extends RuntimeException {

    private final String shortCode;

    public PasswordRequiredException(String shortCode) {
        super("Password required for short URL: " + shortCode);
        this.shortCode = shortCode;
    }

    public String getShortCode() {
        return shortCode;
    }
}