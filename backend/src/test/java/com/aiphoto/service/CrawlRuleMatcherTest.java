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

    @Test
    void extractsT66yEssDataInsteadOfPlaceholderAttribute() {
        var element = Jsoup.parse("""
                <div id='conttpc'>
                  <img iyl-data='http://a.d/adblo_ck.jpg'
                       ess-data='https://23img.com/i/2026/09/12/photo.jpg'>
                </div>
                """).selectFirst("#conttpc img");

        String result = CrawlRuleMatcher.extractImageUrl(
                element, "ess-data,src,data-original,data-src,srcset",
                URI.create("https://t66y.com/htm_data/2609/16/1.html"));

        assertThat(result).isEqualTo("https://23img.com/i/2026/09/12/photo.jpg");
    }

    @Test
    void t66ySelectorsExcludeStickyRowsAndOnlyFollowNextPage() {
        var document = Jsoup.parse("""
                <div class='pages'>
                  <a href='thread0806.php?fid=16&page=2'>2</a>
                  <a href='thread0806.php?fid=16&page=2'>下一頁</a>
                  <a href='thread0806.php?fid=16&page=971'>＞</a>
                </div>
                <table>
                  <tbody id='cate_thread'><tr><td><h3><a href='/htm_data/old/16/1.html'>置顶</a></h3></td></tr></tbody>
                  <tbody id='tbody'><tr><td><h3><a href='/htm_data/2609/16/2.html'>普通主题</a></h3></td></tr></tbody>
                </table>
                """);

        assertThat(document.select("#tbody h3 a[href^='/htm_data/'][href$='.html']"))
                .extracting(element -> element.text())
                .containsExactly("普通主题");
        assertThat(document.select(".pages a[href^='thread0806.php']:matchesOwn(^下一頁$)"))
                .extracting(element -> element.text())
                .containsExactly("下一頁");
    }
}
