package com.urlshortener.dto;

import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.constraints.URL;
import java.time.Instant;

public record CreateUrlRequest(
        @NotBlank
        @URL(message = "longUrl must be a valid URL")
        String longUrl,
        String customAlias,
        Instant expiresAt,
        String password) {
}