package com.aiphoto.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

class CrawlRuleMatcherTest {

    @Test
    void appliesIncludesBeforeExcludesCaseInsensitively() {
        assertThat(CrawlRuleMatcher.matches(
                "https://example.com/GALLERY/photo-1.jpg", "/gallery/,/album/", "thumb,avatar"))
                .isTrue();
        assertThat(CrawlRuleMatcher.matches(
                "https://example.com/gallery/thumb/photo-1.jpg", "/gallery/", "thumb,avatar"))
                .isFalse();
        assertThat(CrawlRuleMatcher.matches(
                "https://example.com/news/photo-1.jpg", "/gallery/", ""))
                .isFalse();
    }

    @Test
    void emptyRulesAllowTheUrl() {
        assertThat(CrawlRuleMatcher.matches("https://example.com/photo.jpg", "", null)).isTrue();
    }

    @Test
    void extractsLargestSrcsetCandidateAgainstTheFinalPageUrl() {
        var element = Jsoup.parse(
                "<img src='small.jpg' srcset='small.jpg 320w, /images/large.jpg 1600w'>")
                .selectFirst("img");

        String result = CrawlRuleMatcher.extractImageUrl(
                element, "data-original,srcset,src", URI.create("https://example.com/gallery/page/1"));

        assertThat(result).isEqualTo("https://example.com/images/large.jpg");
    }

    @Test
    void ignoresNonHttpImageCandidates() {
        var element = Jsoup.parse("<img src='data:image/png;base64,AAAA'>").selectFirst("img");

        String result = CrawlRuleMatcher.extractImageUrl(
                element, "src", URI.create("https://example.com/gallery/"));

        assertThat(result).isNull();
    }
}
