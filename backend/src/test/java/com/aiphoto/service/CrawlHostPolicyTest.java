package com.aiphoto.service;

import static org.assertj.core.api.Assertions.*;

import com.aiphoto.entity.CrawlRule;
import com.aiphoto.entity.CrawlSite;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import org.junit.jupiter.api.Test;

class CrawlHostPolicyTest {
    @Test void emptyBlacklistAllowsUnlistedImageHosts() {
        assertThat(CrawlHostPolicy.parse(null)).isEmpty();
        assertThat(CrawlHostPolicy.parse("  ")).isEmpty();
        assertThatCode(() -> CrawlHostPolicy.requireNotBlocked(URI.create("https://66img.cc/image.jpg"), ""))
                .doesNotThrowAnyException();
    }

    @Test void normalizesSeparatorsCaseAndTrailingDotWithoutDuplicates() {
        assertThat(CrawlHostPolicy.normalizeBlacklist(" ADS.Example.COM.,ads.example.com， trackers.example\nspam.example "))
                .isEqualTo("ads.example.com,trackers.example,spam.example");
    }

    @Test void blocksDomainAndSubdomainsButNotSimilarDomainSuffixes() {
        String blacklist = "66img.cc";
        for (String host : new String[] {"66img.cc", "cdn.66img.cc", "CDN.66IMG.CC."}) {
            assertThatThrownBy(() -> CrawlHostPolicy.requireNotBlocked(URI.create("https://" + host + "/"), blacklist))
                    .hasMessageContaining("禁止访问列表");
        }
        for (String host : new String[] {"other66img.cc", "66img.cc.example.com"}) {
            assertThatCode(() -> CrawlHostPolicy.requireNotBlocked(URI.create("https://" + host + "/"), blacklist))
                    .doesNotThrowAnyException();
        }
    }

    @Test void rejectsUrlsPortsPathsAndWildcardsInDomainEntries() {
        for (String value : new String[] {"https://66img.cc", "66img.cc/path", "66img.cc:443", "*.66img.cc"}) {
            assertThatThrownBy(() -> CrawlHostPolicy.normalizeBlacklist(value))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test void legacySiteAndRuleSnapshotsDoNotTurnAllowedDomainsIntoBlockedDomains() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        String legacy = "{\"allowedHosts\":\"t66y.com,66img.cc\"}";
        CrawlRule rule = mapper.readValue(legacy, CrawlRule.class);
        CrawlSite site = mapper.readValue(legacy, CrawlSite.class);
        assertThat(rule.getBlockedHosts()).isEmpty();
        assertThat(site.getBlockedHosts()).isEmpty();
        assertThat(mapper.writeValueAsString(rule)).doesNotContain("allowedHosts");
        assertThat(mapper.readValue("{\"blockedHosts\":\"66img.cc\"}", CrawlRule.class).getBlockedHosts())
                .isEqualTo("66img.cc");
    }
}
