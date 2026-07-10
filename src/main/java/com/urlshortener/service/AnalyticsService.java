package com.urlshortener.service;

import com.urlshortener.dto.AnalyticsResponse;
import com.urlshortener.dto.BreakdownItem;
import com.urlshortener.dto.DailyClickCount;
import com.urlshortener.entity.UrlMapping;
import com.urlshortener.exception.UrlNotFoundException;
import com.urlshortener.repository.ClickEventRepository;
import com.urlshortener.repository.UrlMappingRepository;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class AnalyticsService {

    private final ClickEventRepository clickEventRepository;
    private final UrlMappingRepository urlMappingRepository;
    private final UrlService urlService;

    public AnalyticsService(ClickEventRepository clickEventRepository,
                             UrlMappingRepository urlMappingRepository,
                             UrlService urlService) {
        this.clickEventRepository = clickEventRepository;
        this.urlMappingRepository = urlMappingRepository;
        this.urlService = urlService;
    }

    public AnalyticsResponse getAnalytics(String shortCode) {
        UrlMapping mapping = urlMappingRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new UrlNotFoundException("Short URL not found: " + shortCode));
        urlService.assertOwnership(mapping);

        List<Object[]> rawResults = clickEventRepository.countClicksGroupedByDay(mapping.getId());

        List<DailyClickCount> clicksByDay = rawResults.stream()
                .map(row -> {
                    LocalDate date = toLocalDate(row[0]);
                    long count = ((Number) row[1]).longValue();
                    return new DailyClickCount(date, count);
                })
                .collect(Collectors.toList());

        long totalClicks = mapping.getClickCount() == null ? 0L : mapping.getClickCount();

        List<BreakdownItem> deviceBreakdown = toBreakdown(clickEventRepository.countByDeviceType(mapping.getId()));
        List<BreakdownItem> browserBreakdown = toBreakdown(clickEventRepository.countByBrowser(mapping.getId()));
        List<BreakdownItem> referrerBreakdown = toBreakdown(clickEventRepository.countByReferrer(mapping.getId()));

        return new AnalyticsResponse(mapping.getShortCode(), mapping.getLongUrl(), totalClicks, clicksByDay,
                deviceBreakdown, browserBreakdown, referrerBreakdown);
    }

    private List<BreakdownItem> toBreakdown(List<Object[]> rows) {
        return rows.stream()
                .map(row -> new BreakdownItem((String) row[0], ((Number) row[1]).longValue()))
                .collect(Collectors.toList());
    }

    private LocalDate toLocalDate(Object value) {
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        if (value instanceof Date sqlDate) {
            return sqlDate.toLocalDate();
        }
        if (value instanceof java.util.Date utilDate) {
            return utilDate.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
        }
        throw new IllegalStateException("Unexpected date type: " + value.getClass());
    }
}