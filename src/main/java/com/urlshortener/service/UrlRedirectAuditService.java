package com.urlshortener.service;

import com.urlshortener.entity.ClickEvent;
import com.urlshortener.entity.UrlMapping;
import com.urlshortener.repository.ClickEventRepository;
import com.urlshortener.repository.UrlMappingRepository;
import com.urlshortener.util.UserAgentParser;
import java.util.Optional;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UrlRedirectAuditService {

    private final UrlMappingRepository urlMappingRepository;
    private final ClickEventRepository clickEventRepository;

    public UrlRedirectAuditService(UrlMappingRepository urlMappingRepository,
                                   ClickEventRepository clickEventRepository) {
        this.urlMappingRepository = urlMappingRepository;
        this.clickEventRepository = clickEventRepository;
    }

    @Async
    @Transactional
    public void recordRedirect(String shortCode, String referrer, String ipAddress, String userAgent) {
        Optional<UrlMapping> mappingOptional = urlMappingRepository.findByShortCode(shortCode);
        if (mappingOptional.isEmpty()) {
            return;
        }

        UrlMapping mapping = mappingOptional.get();
        mapping.setClickCount((mapping.getClickCount() == null ? 0L : mapping.getClickCount()) + 1);
        urlMappingRepository.save(mapping);

        ClickEvent clickEvent = new ClickEvent();
        clickEvent.setUrlMapping(mapping);
        clickEvent.setReferrer(referrer);
        clickEvent.setIpAddress(ipAddress);
        clickEvent.setDeviceType(UserAgentParser.parseDeviceType(userAgent));
        clickEvent.setBrowser(UserAgentParser.parseBrowser(userAgent));
        clickEvent.setOperatingSystem(UserAgentParser.parseOs(userAgent));
        clickEventRepository.save(clickEvent);
    }
}