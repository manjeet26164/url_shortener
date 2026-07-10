package com.urlshortener.service;

import com.urlshortener.entity.UrlMapping;
import com.urlshortener.entity.User;
import com.urlshortener.exception.InvalidAliasException;
import com.urlshortener.exception.InvalidPasswordException;
import com.urlshortener.exception.PasswordRequiredException;
import com.urlshortener.exception.RateLimitExceededException;
import com.urlshortener.exception.UrlExpiredException;
import com.urlshortener.exception.UrlNotFoundException;
import com.urlshortener.repository.ClickEventRepository;
import com.urlshortener.repository.UrlMappingRepository;
import com.urlshortener.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class UrlService {

    private static final Logger log = LoggerFactory.getLogger(UrlService.class);

    private static final Pattern CUSTOM_ALIAS_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{1,64}$");

    private final UrlMappingRepository urlMappingRepository;
    private final ClickEventRepository clickEventRepository;
    private final UrlEncodingService urlEncodingService;
    private final UserRepository userRepository;
    private final UrlCacheService urlCacheService;
    private final UrlRedirectAuditService urlRedirectAuditService;
    private final RateLimiterService rateLimiterService;
    private final PasswordEncoder passwordEncoder;

    public UrlService(UrlMappingRepository urlMappingRepository,
            ClickEventRepository clickEventRepository,
            UrlEncodingService urlEncodingService,
            UserRepository userRepository,
            UrlCacheService urlCacheService,
            UrlRedirectAuditService urlRedirectAuditService,
            RateLimiterService rateLimiterService,
            PasswordEncoder passwordEncoder) {
        this.urlMappingRepository = urlMappingRepository;
        this.clickEventRepository = clickEventRepository;
        this.urlEncodingService = urlEncodingService;
        this.userRepository = userRepository;
        this.urlCacheService = urlCacheService;
        this.urlRedirectAuditService = urlRedirectAuditService;
        this.rateLimiterService = rateLimiterService;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UrlMapping createShortUrl(String longUrl, String customAlias, Instant expiresAt, String password) {
        currentUser().ifPresent(rateLimiterService::checkCreateLimit);
        String passwordHash = hashPassword(password);

        if (customAlias != null && !customAlias.isBlank()) {
            validateCustomAlias(customAlias);

            UrlMapping mapping = new UrlMapping();
            mapping.setLongUrl(longUrl);
            mapping.setShortCode(customAlias);
            mapping.setCustomAlias(customAlias);
            mapping.setExpiresAt(expiresAt);
            mapping.setClickCount(0L);
            mapping.setPasswordHash(passwordHash);
            currentUser().ifPresent(mapping::setUser);
            UrlMapping saved = urlMappingRepository.save(mapping);
            cacheIfUnprotected(saved);
            return saved;
        }

        UrlMapping placeholder = new UrlMapping();
        placeholder.setLongUrl(longUrl);
        placeholder.setShortCode("pending-" + UUID.randomUUID());
        placeholder.setExpiresAt(expiresAt);
        placeholder.setClickCount(0L);
        placeholder.setPasswordHash(passwordHash);
        currentUser().ifPresent(placeholder::setUser);

        UrlMapping saved = urlMappingRepository.save(placeholder);
        String generatedShortCode = urlEncodingService.encode(saved.getId());
        saved.setShortCode(generatedShortCode);
        UrlMapping persisted = urlMappingRepository.save(saved);
        cacheIfUnprotected(persisted);
        return persisted;
    }

    private String hashPassword(String password) {
        if (password == null || password.isBlank()) {
            return null;
        }
        return passwordEncoder.encode(password);
    }

    private void cacheIfUnprotected(UrlMapping mapping) {
        if (!mapping.isPasswordProtected()) {
            urlCacheService.put(mapping);
        }
    }

    @Transactional
    public String getOriginalUrl(String shortCode) {
        return getOriginalUrl(shortCode, null, null, null);
    }

    @Transactional
    public String getOriginalUrl(String shortCode, String referrer, String ipAddress, String userAgent) {
        Optional<String> cachedUrl = urlCacheService.get(shortCode);
        if (cachedUrl.isPresent()) {
            log.info("CACHE HIT for {}", shortCode);
            urlRedirectAuditService.recordRedirect(shortCode, referrer, ipAddress, userAgent);
            return cachedUrl.get();
        }

        log.info("CACHE MISS for {}", shortCode);
        UrlMapping mapping = findActiveMapping(shortCode);

        if (mapping.isPasswordProtected()) {
            throw new PasswordRequiredException(shortCode);
        }

        urlCacheService.put(mapping);
        urlRedirectAuditService.recordRedirect(shortCode, referrer, ipAddress, userAgent);

        return mapping.getLongUrl();
    }

    @Transactional
    public String unlockWithPassword(String shortCode, String rawPassword, String referrer, String ipAddress,
            String userAgent) {
        UrlMapping mapping = findActiveMapping(shortCode);

        if (!mapping.isPasswordProtected() || !passwordEncoder.matches(rawPassword, mapping.getPasswordHash())) {
            throw new InvalidPasswordException("Incorrect password for short URL: " + shortCode);
        }

        urlRedirectAuditService.recordRedirect(shortCode, referrer, ipAddress, userAgent);
        return mapping.getLongUrl();
    }

    private UrlMapping findActiveMapping(String shortCode) {
        UrlMapping mapping = urlMappingRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new UrlNotFoundException("Short URL not found: " + shortCode));

        Instant now = Instant.now();
        if (mapping.getExpiresAt() != null && !mapping.getExpiresAt().isAfter(now)) {
            urlCacheService.evict(shortCode);
            throw new UrlExpiredException("Short URL has expired: " + shortCode);
        }

        return mapping;
    }

    @Transactional(readOnly = true)
    public List<UrlMapping> getUserUrls() {
        Optional<User> currentUser = currentUser();
        return currentUser
                .map(urlMappingRepository::findByUserOrderByCreatedAtDesc)
                .orElseGet(() -> urlMappingRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    @Transactional
    public void deleteShortUrl(String shortCode) {
        UrlMapping mapping = urlMappingRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new UrlNotFoundException("Short URL not found: " + shortCode));
        assertOwnership(mapping);
        clickEventRepository.deleteByUrlMapping(mapping);
        urlMappingRepository.delete(mapping);
        urlCacheService.evict(shortCode);
    }

    void assertOwnership(UrlMapping mapping) {
        Long ownerId = mapping.getUser() != null ? mapping.getUser().getId() : null;
        Long requesterId = currentUser().map(User::getId).orElse(null);

        if (ownerId == null || requesterId == null || !ownerId.equals(requesterId)) {
            throw new UrlNotFoundException("Short URL not found: " + mapping.getShortCode());
        }
    }

    private void validateCustomAlias(String customAlias) {
        if (!CUSTOM_ALIAS_PATTERN.matcher(customAlias).matches()) {
            throw new InvalidAliasException("Custom alias must contain only letters, numbers, hyphen, or underscore");
        }

        if (urlMappingRepository.existsByShortCode(customAlias)
                || urlMappingRepository.existsByCustomAlias(customAlias)) {
            throw new InvalidAliasException("Custom alias is already in use");
        }
    }

    private Optional<User> currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }

        return userRepository.findByEmail(authentication.getName());
    }
}