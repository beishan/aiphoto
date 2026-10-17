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
    void explainsEveryMatchingIncludeAndExcludeFilter() {
        assertThat(CrawlRuleMatcher.filterReason(
                "https://example.com/gallery/thumb/photo.jpg", "/gallery/,/album/", "thumb,avatar"))
                .isEqualTo("命中排除片段：thumb");
        assertThat(CrawlRuleMatcher.filterReason(
                "https://example.com/avatar/photo.jpg", "/gallery/,/album/", "thumb,avatar"))
                .isEqualTo("未命中任一必含片段（要求命中一个：/gallery/、/album/）；命中排除片段：avatar");
        assertThat(CrawlRuleMatcher.filterReason(
                "https://example.com/gallery/photo.jpg", "/gallery/", "thumb,avatar")).isNull();
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
    void prefersOriginalImageAttributeToViewerLinkAndDisplaySource() {
        var element = Jsoup.parse("""
                <img iyl-data="http://a.d/adblo_ck.jpg"
                     data-link="https://23img.com/l/?i=/i/2026/10/15/12azaff.jpg"
                     ess-data="https://23img.com/i/2026/10/15/12azaff.jpg"
                     referrerpolicy="no-referrer" src="https://23img.com/thumbnail.jpg">
                """).selectFirst("img");
        assertThat(CrawlRuleMatcher.extractImageUrl(element,
                "ess-data,src,data-original,data-src,srcset", URI.create("https://t66y.com/thread.html")))
                .isEqualTo("https://23img.com/i/2026/10/15/12azaff.jpg");
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
