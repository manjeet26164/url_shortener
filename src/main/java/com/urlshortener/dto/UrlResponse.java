package com.urlshortener.dto;

import java.time.Instant;

public record UrlResponse(
        Long id,
        String shortCode,
        String longUrl,
        String fullShortUrl,
        Instant createdAt,
        Instant expiresAt,
        Long clickCount,
        boolean passwordProtected) {
}