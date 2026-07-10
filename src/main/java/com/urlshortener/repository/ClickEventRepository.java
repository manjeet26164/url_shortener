package com.urlshortener.repository;

import com.urlshortener.entity.ClickEvent;
import com.urlshortener.entity.UrlMapping;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClickEventRepository extends JpaRepository<ClickEvent, Long> {

    List<ClickEvent> findByUrlMappingIdOrderByClickedAtDesc(Long urlMappingId);

    @Query(value = "SELECT device_type, COUNT(*) FROM click_events WHERE url_mapping_id = :urlMappingId GROUP BY device_type",
            nativeQuery = true)
    List<Object[]> countByDeviceType(@Param("urlMappingId") Long urlMappingId);

    @Query(value = "SELECT browser, COUNT(*) FROM click_events WHERE url_mapping_id = :urlMappingId GROUP BY browser",
            nativeQuery = true)
    List<Object[]> countByBrowser(@Param("urlMappingId") Long urlMappingId);

    @Query(value = "SELECT COALESCE(NULLIF(referrer, ''), 'Direct'), COUNT(*) FROM click_events WHERE url_mapping_id = :urlMappingId GROUP BY COALESCE(NULLIF(referrer, ''), 'Direct')",
            nativeQuery = true)
    List<Object[]> countByReferrer(@Param("urlMappingId") Long urlMappingId);

    @Query(value = """
        SELECT CAST(clicked_at AS date) AS clickDay, COUNT(*) AS clickCount
        FROM click_events
        WHERE url_mapping_id = :urlMappingId
        GROUP BY CAST(clicked_at AS date)
        ORDER BY clickDay ASC
        """, nativeQuery = true)
    List<Object[]> countClicksGroupedByDay(@Param("urlMappingId") Long urlMappingId);
    void deleteByUrlMapping(UrlMapping urlMapping);
}