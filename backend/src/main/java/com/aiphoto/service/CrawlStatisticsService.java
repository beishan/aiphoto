package com.aiphoto.service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Bounded database aggregation; never loads the account's image records into memory. */
@Service
@RequiredArgsConstructor
public class CrawlStatisticsService {
    private final NamedParameterJdbcTemplate jdbc;
    static final int MAX_BUCKETS = 1440;

    // Local timestamps follow the application's timezone, including epoch-based image timings.
    static final String SQL = """
            WITH events AS (
                SELECT h.first_seen_at AS event_at, s.id AS site_id, s.name AS site_name,
                       'NEW_PAGE' AS kind, '' AS outcome, NULL::bigint AS duration
                FROM crawl_seen_pages h JOIN crawl_sites s ON s.id = h.site_id
                WHERE s.owner_id = :owner
                  AND h.first_seen_at >= :start AND h.first_seen_at < :end
                UNION ALL
                SELECT a.created_at, s.id, s.name, 'NEW_IMAGE', '', NULL::bigint
                FROM crawl_assets a JOIN crawl_jobs j ON j.id = a.job_id
                JOIN crawl_sites s ON s.id = j.site_id
                WHERE j.owner_id = :owner
                  AND a.created_at >= :start AND a.created_at < :end
                UNION ALL
                SELECT j.finished_at, s.id, s.name, 'PAGE',
                       CASE WHEN p.status = 'SKIPPED' THEN 'SKIPPED' ELSE j.status END, NULL::bigint
                FROM crawl_pages p JOIN crawl_jobs j ON j.id = p.job_id
                JOIN crawl_sites s ON s.id = j.site_id
                WHERE j.owner_id = :owner AND p.included = TRUE AND p.collection_job_id IS NULL
                  AND j.phase IN ('DOWNLOAD', 'REVIEW')
                  AND j.status IN ('COMPLETED', 'PARTIAL', 'FAILED', 'CANCELLED')
                  AND j.finished_at >= :start AND j.finished_at < :end
                UNION ALL
                SELECT i.event_at, i.site_id, i.site_name, 'IMAGE', i.outcome, i.duration
                FROM (
                    SELECT s.id AS site_id, s.name AS site_name,
                        COALESCE(CASE WHEN a.download_started_at_millis IS NOT NULL
                            AND a.download_duration_millis >= 0 THEN
                            to_timestamp((a.download_started_at_millis + a.download_duration_millis)
                                / 1000.0) AT TIME ZONE :source_zone END, j.finished_at) AS event_at,
                        CASE WHEN a.status = 'FAILED' THEN 'FAILED'
                             WHEN a.status = 'SKIPPED' THEN 'SKIPPED' ELSE 'SUCCESS' END AS outcome,
                        CASE WHEN a.download_started_at_millis IS NOT NULL
                            AND a.download_duration_millis >= 0 AND a.status <> 'SKIPPED'
                            THEN a.download_duration_millis END AS duration
                    FROM crawl_assets a JOIN crawl_jobs j ON j.id = a.job_id
                    JOIN crawl_sites s ON s.id = j.site_id
                    WHERE j.owner_id = :owner
                      AND a.status IN ('DOWNLOADED', 'IMPORTED', 'DELETED', 'DUPLICATE', 'FAILED', 'SKIPPED')
                ) i WHERE i.event_at >= :start AND i.event_at < :end
            )
            SELECT date_trunc(:grain, event_at AT TIME ZONE :source_zone AT TIME ZONE :zone) AS bucket, site_id, site_name, kind, outcome,
                CASE WHEN duration IS NULL THEN -1 WHEN duration < 1000 THEN 0
                     WHEN duration < 5000 THEN 1 WHEN duration < 15000 THEN 2
                     WHEN duration < 60000 THEN 3 ELSE 4 END AS band,
                count(*) AS amount, count(duration) AS samples,
                COALESCE(sum(duration), 0) AS duration_sum,
                COALESCE(min(duration), 0) AS duration_min,
                COALESCE(max(duration), 0) AS duration_max
            FROM events GROUP BY bucket, site_id, site_name, kind, outcome, band
            ORDER BY bucket, site_id, kind, outcome, band
            """;

    public Statistics statistics(Long ownerId, String granularity, LocalDateTime start, LocalDateTime end) {
        return statistics(ownerId, granularity, start, end, null);
    }

    @Transactional(readOnly = true)
    public Statistics statistics(Long ownerId, String granularity, LocalDateTime start,
            LocalDateTime end, String timezone) {
        ChronoUnit unit = unit(granularity);
        if (start == null || end == null || !start.isBefore(end)) {
            throw new IllegalArgumentException("请选择有效的统计起止时间");
        }
        LocalDateTime first = start.truncatedTo(unit);
        long bucketCount = unit.between(first, end.truncatedTo(unit))
                + (end.equals(end.truncatedTo(unit)) ? 0 : 1);
        if (bucketCount > MAX_BUCKETS) {
            throw new IllegalArgumentException("最多显示 1440 个时间段，请缩短范围或改用更粗的日期维度");
        }
        ZoneId sourceZone = ZoneId.systemDefault();
        ZoneId reportZone;
        try {
            reportZone = timezone == null ? sourceZone : ZoneId.of(timezone);
        } catch (java.time.DateTimeException invalidZone) {
            throw new IllegalArgumentException("无效的统计时区");
        }
        String zone = reportZone.getId();
        LocalDateTime sourceStart = start.atZone(reportZone).withZoneSameInstant(sourceZone).toLocalDateTime();
        LocalDateTime sourceEnd = end.atZone(reportZone).withZoneSameInstant(sourceZone).toLocalDateTime();
        var parameters = new MapSqlParameterSource()
                .addValue("owner", ownerId).addValue("grain", granularity.toLowerCase(Locale.ROOT))
                .addValue("start", Timestamp.valueOf(sourceStart)).addValue("end", Timestamp.valueOf(sourceEnd))
                .addValue("source_zone", sourceZone.getId()).addValue("zone", zone);
        var rows = jdbc.queryForList(SQL, parameters);
        var buckets = new LinkedHashMap<LocalDateTime, Counts>();
        for (LocalDateTime at = first; at.isBefore(end); at = at.plus(1, unit)) {
            buckets.put(at, new Counts());
        }
        Counts total = new Counts();
        Map<Long, SiteCounts> sites = new LinkedHashMap<>();
        for (var row : rows) {
            LocalDateTime at = ((Timestamp) row.get("bucket")).toLocalDateTime();
            Counts bucket = buckets.get(at);
            if (bucket == null) continue;
            long siteId = number(row, "site_id");
            SiteCounts site = sites.computeIfAbsent(siteId,
                    id -> new SiteCounts(id, (String) row.get("site_name"), new Counts()));
            total.add(row);
            bucket.add(row);
            site.counts().add(row);
        }
        return new Statistics(granularity.toUpperCase(Locale.ROOT), start, end, zone, total,
                buckets.entrySet().stream().map(e -> new Bucket(e.getKey(), e.getValue())).toList(),
                new ArrayList<>(sites.values()));
    }

    static ChronoUnit unit(String value) {
        return switch (value == null ? "" : value.toUpperCase(Locale.ROOT)) {
            case "DAY" -> ChronoUnit.DAYS;
            case "HOUR" -> ChronoUnit.HOURS;
            case "MINUTE" -> ChronoUnit.MINUTES;
            default -> throw new IllegalArgumentException("统计维度仅支持 DAY、HOUR、MINUTE");
        };
    }

    private static long number(Map<String, Object> row, String key) {
        return ((Number) row.get(key)).longValue();
    }

    public record Statistics(String granularity, LocalDateTime start, LocalDateTime end,
            String timezone, Counts total, List<Bucket> buckets, List<SiteCounts> sites) {}
    public record Bucket(LocalDateTime time, Counts counts) {}
    public record SiteCounts(Long siteId, String siteName, Counts counts) {}

    @lombok.Getter
    public static class Counts {
        private long newPages;
        private long newImages;
        private long pagesSuccess;
        private long pagesFailed;
        private long pagesPartial;
        private long pagesCancelled;
        private long pagesSkipped;
        private long imagesSuccess;
        private long imagesFailed;
        private long imagesSkipped;
        private long durationSamples;
        private double durationTotalMillis;
        private long durationMinMillis;
        private long durationMaxMillis;
        private final long[] durationBands = new long[5];

        public double getDurationAverageMillis() {
            return durationSamples == 0 ? 0 : durationTotalMillis / durationSamples;
        }
        public long getPagesCollected() {
            return pagesSuccess + pagesFailed + pagesPartial + pagesCancelled + pagesSkipped;
        }
        void add(Map<String, Object> row) {
            long amount = number(row, "amount");
            String outcome = (String) row.get("outcome");
            switch ((String) row.get("kind")) {
                case "NEW_PAGE" -> newPages += amount;
                case "NEW_IMAGE" -> newImages += amount;
                case "PAGE" -> {
                    switch (outcome) {
                        case "COMPLETED" -> pagesSuccess += amount;
                        case "PARTIAL" -> pagesPartial += amount;
                        case "FAILED" -> pagesFailed += amount;
                        case "CANCELLED" -> pagesCancelled += amount;
                        case "SKIPPED" -> pagesSkipped += amount;
                        default -> { }
                    }
                }
                case "IMAGE" -> {
                    switch (outcome) {
                        case "SUCCESS" -> imagesSuccess += amount;
                        case "FAILED" -> imagesFailed += amount;
                        case "SKIPPED" -> imagesSkipped += amount;
                        default -> { }
                    }
                    long samples = number(row, "samples");
                    if (samples > 0) {
                        long min = number(row, "duration_min");
                        durationMinMillis = durationSamples == 0 ? min : Math.min(durationMinMillis, min);
                        durationMaxMillis = Math.max(durationMaxMillis, number(row, "duration_max"));
                        durationSamples += samples;
                        durationTotalMillis += ((Number) row.get("duration_sum")).doubleValue();
                        durationBands[((Number) row.get("band")).intValue()] += samples;
                    }
                }
                default -> { }
            }
        }
    }
}
