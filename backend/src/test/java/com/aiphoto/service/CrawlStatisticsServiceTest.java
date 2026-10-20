package com.aiphoto.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@ExtendWith(MockitoExtension.class)
class CrawlStatisticsServiceTest {
    @Mock NamedParameterJdbcTemplate jdbc;
    private static final LocalDateTime START = LocalDateTime.of(2026, 10, 20, 0, 0);

    @Test void fillsEmptyBucketsAndComputesWeightedDurationAcrossSites() {
        when(jdbc.queryForList(eq(CrawlStatisticsService.SQL), any(MapSqlParameterSource.class)))
                .thenReturn(List.of(
                        row(START, 1L, "NEW_PAGE", "", 3, 0, 0, 0, 0, -1),
                        row(START, 1L, "IMAGE", "SUCCESS", 2, 2, 3000, 1000, 2000, 1),
                        row(START.plusDays(2), 2L, "IMAGE", "FAILED", 1, 1, 60000, 60000, 60000, 4),
                        row(START, 1L, "PAGE", "PARTIAL", 1, 0, 0, 0, 0, -1)));
        var result = new CrawlStatisticsService(jdbc).statistics(9L, "DAY", START, START.plusDays(3));
        assertThat(result.buckets()).hasSize(3);
        assertThat(result.buckets().get(1).counts().getNewPages()).isZero();
        assertThat(result.total().getNewPages()).isEqualTo(3);
        assertThat(result.total().getPagesPartial()).isEqualTo(1);
        assertThat(result.total().getPagesCollected()).isEqualTo(1);
        assertThat(result.total().getImagesSuccess()).isEqualTo(2);
        assertThat(result.total().getImagesFailed()).isEqualTo(1);
        assertThat(result.total().getDurationAverageMillis()).isEqualTo(21000);
        assertThat(result.total().getDurationMinMillis()).isEqualTo(1000);
        assertThat(result.total().getDurationMaxMillis()).isEqualTo(60000);
        assertThat(result.total().getDurationBands()).containsExactly(0, 2, 0, 0, 1);
        assertThat(result.sites()).hasSize(2);
    }

    @Test void passesAuthenticatedOwnerAndExactHalfOpenRangeAsSqlParameters() {
        var start = START.plusMinutes(15);
        var end = START.plusHours(2).plusMinutes(10);
        when(jdbc.queryForList(eq(CrawlStatisticsService.SQL), any(MapSqlParameterSource.class)))
                .thenAnswer(call -> {
                    MapSqlParameterSource params = call.getArgument(1);
                    assertThat(params.getValue("owner")).isEqualTo(9L);
                    assertThat(params.getValue("grain")).isEqualTo("hour");
                    assertThat(params.getValue("start")).isEqualTo(Timestamp.valueOf(start));
                    assertThat(params.getValue("end")).isEqualTo(Timestamp.valueOf(end));
                    assertThat(params.getValue("zone")).isNotNull();
                    return List.of();
                });
        var result = new CrawlStatisticsService(jdbc).statistics(9L, "HOUR", start, end);
        assertThat(result.buckets()).hasSize(3);
        assertThat(result.buckets().getFirst().time()).isEqualTo(START);
        assertThat(result.total().getDurationAverageMillis()).isZero();
    }

    @Test void convertsDeviceRangeToServerTimeBeforeFiltering() {
        var sourceZone = java.time.ZoneId.systemDefault();
        var reportZone = java.time.ZoneId.of("America/New_York");
        when(jdbc.queryForList(eq(CrawlStatisticsService.SQL), any(MapSqlParameterSource.class)))
                .thenAnswer(call -> {
                    MapSqlParameterSource params = call.getArgument(1);
                    assertThat(params.getValue("zone")).isEqualTo("America/New_York");
                    assertThat(params.getValue("source_zone")).isEqualTo(sourceZone.getId());
                    var expected = START.atZone(reportZone).withZoneSameInstant(sourceZone).toLocalDateTime();
                    assertThat(params.getValue("start")).isEqualTo(Timestamp.valueOf(expected));
                    return List.of();
                });
        var result = new CrawlStatisticsService(jdbc).statistics(
                9L, "DAY", START, START.plusDays(1), "America/New_York");
        assertThat(result.timezone()).isEqualTo("America/New_York");
        assertThat(result.start()).isEqualTo(START);
        assertThat(result.buckets()).hasSize(1);
    }

    @Test void supportsExactly1440MinuteBucketsWithoutAnExtraEndBucket() {
        when(jdbc.queryForList(eq(CrawlStatisticsService.SQL), any(MapSqlParameterSource.class)))
                .thenReturn(List.of());
        var result = new CrawlStatisticsService(jdbc).statistics(9L, "MINUTE", START, START.plusDays(1));
        assertThat(result.buckets()).hasSize(1440);
        assertThat(result.buckets().getLast().time()).isEqualTo(START.plusDays(1).minusMinutes(1));
    }

    @Test void excludesMissingTimingSamplesButKeepsSuccessfulImageCounts() {
        when(jdbc.queryForList(eq(CrawlStatisticsService.SQL), any(MapSqlParameterSource.class)))
                .thenReturn(List.of(row(START, 1L, "IMAGE", "SUCCESS", 7, 0, 0, 0, 0, -1)));
        var result = new CrawlStatisticsService(jdbc).statistics(9L, "DAY", START, START.plusDays(1));
        assertThat(result.total().getImagesSuccess()).isEqualTo(7);
        assertThat(result.total().getDurationSamples()).isZero();
        assertThat(result.total().getDurationBands()).containsOnly(0L);
    }

    @Test void rejectsInvalidAndOversizedRangesBeforeQueryingDatabase() {
        var service = new CrawlStatisticsService(jdbc);
        assertThatThrownBy(() -> service.statistics(9L, "WEEK", START, START.plusDays(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.statistics(9L, "DAY", START, START))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.statistics(9L, "DAY", null, START))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.statistics(9L, "MINUTE", START, START.plusDays(1).plusSeconds(1)))
                .hasMessageContaining("1440");
        assertThatThrownBy(() -> service.statistics(9L, "DAY", START, START.plusDays(1), "invalid/zone"))
                .hasMessageContaining("时区");
        verifyNoInteractions(jdbc);
    }

    private Map<String, Object> row(LocalDateTime bucket, Long site, String kind, String outcome,
            long amount, long samples, long sum, long min, long max, int band) {
        Map<String, Object> row = new HashMap<>();
        row.put("bucket", Timestamp.valueOf(bucket));
        row.put("site_id", site);
        row.put("site_name", "网站 " + site);
        row.put("kind", kind);
        row.put("outcome", outcome);
        row.put("amount", amount);
        row.put("samples", samples);
        row.put("duration_sum", sum);
        row.put("duration_min", min);
        row.put("duration_max", max);
        row.put("band", band);
        return row;
    }
}
