package com.urlshortener.controller;

import com.urlshortener.service.UrlService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedirectControllerTest {

    @Test
    void redirectEndpointUsesPublicShortCodeRouteAndPassesMetadata() throws Exception {
        UrlService urlService = mock(UrlService.class);
        RedirectController controller = new RedirectController(urlService);
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);

        when(request.getHeader("Referer")).thenReturn("https://referrer.example");
        when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.10, 10.0.0.2");
        when(request.getHeader("User-Agent")).thenReturn("test-agent");
        when(urlService.getOriginalUrl("abc123", "https://referrer.example", "203.0.113.10", "test-agent")).thenReturn("https://example.com");

        controller.redirectToOriginalUrl("abc123", request, response);

        verify(urlService).getOriginalUrl("abc123", "https://referrer.example", "203.0.113.10", "test-agent");
        verify(response).sendRedirect("https://example.com");
    }
}