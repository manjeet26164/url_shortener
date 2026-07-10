package com.urlshortener.service;

import com.urlshortener.entity.ClickEvent;
import com.urlshortener.entity.UrlMapping;
import com.urlshortener.exception.InvalidAliasException;
import com.urlshortener.exception.RateLimitExceededException;
import com.urlshortener.exception.UrlExpiredException;
import com.urlshortener.exception.UrlNotFoundException;
import com.urlshortener.repository.ClickEventRepository;
import com.urlshortener.repository.UrlMappingRepository;
import com.urlshortener.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UrlServiceTest {

    @Mock
    private UrlMappingRepository urlMappingRepository;

    @Mock
    private ClickEventRepository clickEventRepository;

    @Mock
    private UrlEncodingService urlEncodingService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UrlCacheService urlCacheService;

    @Mock
    private UrlRedirectAuditService urlRedirectAuditService;

    @Mock
    private RateLimiterService rateLimiterService;

    @Mock
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @InjectMocks
    private UrlService urlService;

    @BeforeEach
    void defaultCacheMiss() {
        lenient().when(urlCacheService.get(any())).thenReturn(Optional.empty());
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createShortUrlUsesCustomAliasWhenProvided() {
        when(urlMappingRepository.existsByShortCode("myAlias")).thenReturn(false);
        when(urlMappingRepository.existsByCustomAlias("myAlias")).thenReturn(false);
        when(urlMappingRepository.save(any(UrlMapping.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UrlMapping mapping = urlService.createShortUrl("https://example.com", "myAlias", null, null);

        assertEquals("myAlias", mapping.getShortCode());
        assertEquals("myAlias", mapping.getCustomAlias());
        verify(urlEncodingService, never()).encode(any());
    }

    @Test
    void createShortUrlGeneratesShortCodeFromId() {
        when(urlMappingRepository.save(any(UrlMapping.class))).thenAnswer(invocation -> {
            UrlMapping mapping = invocation.getArgument(0);
            if (mapping.getId() == null) {
                mapping.setId(42L);
            }
            return mapping;
        });
        when(urlEncodingService.encode(42L)).thenReturn("g");

        UrlMapping mapping = urlService.createShortUrl("https://example.com", null, null, null);

        assertEquals(42L, mapping.getId());
        assertEquals("g", mapping.getShortCode());
        verify(urlEncodingService).encode(42L);
    }

    @Test
    void getOriginalUrlThrowsWhenMappingMissing() {
        when(urlMappingRepository.findByShortCode("missing")).thenReturn(Optional.empty());

        assertThrows(UrlNotFoundException.class, () -> urlService.getOriginalUrl("missing"));
    }

    @Test
    void getOriginalUrlThrowsWhenExpired() {
        UrlMapping mapping = new UrlMapping();
        mapping.setId(1L);
        mapping.setShortCode("abc");
        mapping.setLongUrl("https://example.com");
        mapping.setExpiresAt(Instant.now().minusSeconds(60));
        mapping.setClickCount(0L);
        when(urlMappingRepository.findByShortCode("abc")).thenReturn(Optional.of(mapping));

        assertThrows(UrlExpiredException.class, () -> urlService.getOriginalUrl("abc"));
        verify(clickEventRepository, never()).save(any(ClickEvent.class));
    }

    @Test
    void getOriginalUrlIncrementsClickCountAndSavesClickEvent() {
        UrlMapping mapping = new UrlMapping();
        mapping.setId(7L);
        mapping.setShortCode("abc");
        mapping.setLongUrl("https://example.com");
        mapping.setClickCount(2L);
        when(urlMappingRepository.findByShortCode("abc")).thenReturn(Optional.of(mapping));

        String originalUrl = urlService.getOriginalUrl("abc", "https://referrer.example", "127.0.0.1", null);

        assertEquals("https://example.com", originalUrl);
        verify(urlRedirectAuditService).recordRedirect("abc", "https://referrer.example", "127.0.0.1", null);
    }

    @Test
    void getUserUrlsReturnsUrlsSortedByNewestFirst() {
        UrlMapping newest = new UrlMapping();
        newest.setId(2L);
        newest.setShortCode("new");
        newest.setCreatedAt(Instant.now());
        UrlMapping older = new UrlMapping();
        older.setId(1L);
        older.setShortCode("old");
        older.setCreatedAt(Instant.now().minusSeconds(60));
        when(urlMappingRepository.findAll(any(org.springframework.data.domain.Sort.class))).thenReturn(List.of(newest, older));

        List<UrlMapping> results = urlService.getUserUrls();

        assertEquals(List.of(newest, older), results);
    }

    @Test
    void invalidAliasThrowsException() {
        assertThrows(InvalidAliasException.class, () -> urlService.createShortUrl("https://example.com", "bad alias", null, null));
    }

    @Test
    void createShortUrlAssociatesAuthenticatedUser() {
        com.urlshortener.entity.User user = new com.urlshortener.entity.User();
        user.setId(99L);
        user.setEmail("owner@example.com");
        user.setPassword("secret");
        when(userRepository.findByEmail("owner@example.com")).thenReturn(Optional.of(user));
        when(urlMappingRepository.save(any(UrlMapping.class))).thenAnswer(invocation -> {
            UrlMapping mapping = invocation.getArgument(0);
            if (mapping.getId() == null) {
                mapping.setId(123L);
            }
            return mapping;
        });
        when(urlEncodingService.encode(123L)).thenReturn("1Z");
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("owner@example.com", "n/a", List.of(new SimpleGrantedAuthority("ROLE_USER"))));

        UrlMapping mapping = urlService.createShortUrl("https://example.com", null, null,null);

        assertEquals(user, mapping.getUser());
        verify(rateLimiterService).checkCreateLimit(user);
    }

    @Test
    void createShortUrlFailsFastWhenRateLimited() {
        com.urlshortener.entity.User user = new com.urlshortener.entity.User();
        user.setId(99L);
        user.setEmail("owner@example.com");
        user.setPassword("secret");
        when(userRepository.findByEmail("owner@example.com")).thenReturn(Optional.of(user));
        org.mockito.Mockito.doThrow(new RateLimitExceededException(
                "Rate limit exceeded. 0 requests remain. Limit resets at 2026-07-06T12:00:00Z",
                0L,
                Instant.parse("2026-07-06T12:00:00Z")))
                .when(rateLimiterService).checkCreateLimit(user);
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("owner@example.com", "n/a", List.of(new SimpleGrantedAuthority("ROLE_USER"))));

        assertThrows(RateLimitExceededException.class, () -> urlService.createShortUrl("https://example.com", null, null, null));
        org.mockito.Mockito.verify(urlMappingRepository, never()).save(any(UrlMapping.class));
    }

    @Test
    void deleteShortUrlSucceedsForOwner() {
        com.urlshortener.entity.User owner = new com.urlshortener.entity.User();
        owner.setId(99L);
        owner.setEmail("owner@example.com");
        owner.setPassword("secret");
        when(userRepository.findByEmail("owner@example.com")).thenReturn(Optional.of(owner));

        UrlMapping mapping = new UrlMapping();
        mapping.setId(5L);
        mapping.setShortCode("mine");
        mapping.setUser(owner);
        when(urlMappingRepository.findByShortCode("mine")).thenReturn(Optional.of(mapping));
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("owner@example.com", "n/a", List.of(new SimpleGrantedAuthority("ROLE_USER"))));

        urlService.deleteShortUrl("mine");

        verify(urlMappingRepository).delete(mapping);
        verify(urlCacheService).evict("mine");
    }

    @Test
    void deleteShortUrlThrowsWhenRequestedByNonOwner() {
        com.urlshortener.entity.User owner = new com.urlshortener.entity.User();
        owner.setId(99L);
        owner.setEmail("owner@example.com");

        com.urlshortener.entity.User attacker = new com.urlshortener.entity.User();
        attacker.setId(7L);
        attacker.setEmail("attacker@example.com");
        when(userRepository.findByEmail("attacker@example.com")).thenReturn(Optional.of(attacker));

        UrlMapping mapping = new UrlMapping();
        mapping.setId(5L);
        mapping.setShortCode("mine");
        mapping.setUser(owner);
        when(urlMappingRepository.findByShortCode("mine")).thenReturn(Optional.of(mapping));
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("attacker@example.com", "n/a", List.of(new SimpleGrantedAuthority("ROLE_USER"))));

        assertThrows(UrlNotFoundException.class, () -> urlService.deleteShortUrl("mine"));
        verify(urlMappingRepository, never()).delete(any(UrlMapping.class));
        verify(clickEventRepository, never()).deleteByUrlMapping(any(UrlMapping.class));
    }

    @Test
    void deleteShortUrlThrowsWhenNotAuthenticated() {
        UrlMapping mapping = new UrlMapping();
        mapping.setId(5L);
        mapping.setShortCode("mine");
        com.urlshortener.entity.User owner = new com.urlshortener.entity.User();
        owner.setId(99L);
        mapping.setUser(owner);
        when(urlMappingRepository.findByShortCode("mine")).thenReturn(Optional.of(mapping));

        assertThrows(UrlNotFoundException.class, () -> urlService.deleteShortUrl("mine"));
        verify(urlMappingRepository, never()).delete(any(UrlMapping.class));
    }

    @Test
    void getUserUrlsFiltersByAuthenticatedUser() {
        com.urlshortener.entity.User user = new com.urlshortener.entity.User();
        user.setId(99L);
        user.setEmail("owner@example.com");
        user.setPassword("secret");
        when(userRepository.findByEmail("owner@example.com")).thenReturn(Optional.of(user));
        UrlMapping mapping = new UrlMapping();
        mapping.setId(2L);
        mapping.setShortCode("new");
        when(urlMappingRepository.findByUserOrderByCreatedAtDesc(user)).thenReturn(List.of(mapping));
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("owner@example.com", "n/a", List.of(new SimpleGrantedAuthority("ROLE_USER"))));

        List<UrlMapping> results = urlService.getUserUrls();

        assertEquals(List.of(mapping), results);
    }
}