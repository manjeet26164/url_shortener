package com.urlshortener.controller;

import com.urlshortener.dto.CreateUrlRequest;
import com.urlshortener.entity.UrlMapping;
import com.urlshortener.service.UrlService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UrlControllerTest {

    @Test
    void createShortUrlReturnsCreatedResponse() {
        UrlService urlService = mock(UrlService.class);
        UrlController controller = new UrlController(urlService);
        HttpServletRequest request = mock(HttpServletRequest.class);

        UrlMapping mapping = new UrlMapping();
        mapping.setId(10L);
        mapping.setShortCode("abc123");
        mapping.setLongUrl("https://example.com");
        mapping.setCreatedAt(Instant.parse("2026-07-06T00:00:00Z"));
        mapping.setClickCount(0L);
        when(urlService.createShortUrl(any(), any(), any(), any())).thenReturn(mapping);
        when(request.getScheme()).thenReturn("http");
        when(request.getServerName()).thenReturn("localhost");
        when(request.getServerPort()).thenReturn(8080);
        when(request.getContextPath()).thenReturn("");

        var response = controller.createShortUrl(new CreateUrlRequest("https://example.com", null, null, null), request);

        assertEquals(201, response.getStatusCode().value());
        assertEquals(10L, response.getBody().id());
        assertEquals("abc123", response.getBody().shortCode());
        assertEquals("https://example.com", response.getBody().longUrl());
        assertEquals("http://localhost:8080/abc123", response.getBody().fullShortUrl());
    }

    @Test
    void getUserUrlsReturnsMappedResults() {
        UrlService urlService = mock(UrlService.class);
        UrlController controller = new UrlController(urlService);
        HttpServletRequest request = mock(HttpServletRequest.class);

        UrlMapping mapping = new UrlMapping();
        mapping.setId(11L);
        mapping.setShortCode("xyz");
        mapping.setLongUrl("https://example.org");
        mapping.setCreatedAt(Instant.parse("2026-07-06T00:00:00Z"));
        mapping.setClickCount(4L);
        when(urlService.getUserUrls()).thenReturn(List.of(mapping));
        when(request.getScheme()).thenReturn("https");
        when(request.getServerName()).thenReturn("short.ly");
        when(request.getServerPort()).thenReturn(443);
        when(request.getContextPath()).thenReturn("");

        var response = controller.getUserUrls(request);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(1, response.getBody().size());
        assertEquals("https://short.ly/xyz", response.getBody().get(0).fullShortUrl());
    }
}
