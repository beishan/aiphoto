package com.aiphoto.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CrawlAssetTimingTest {
    @Test
    void runningAssetsExposeServerElapsedTime() {
        CrawlAsset asset = new CrawlAsset();
        asset.setStatus(CrawlAsset.Status.RUNNING);
        asset.setDownloadStartedAtMillis(System.currentTimeMillis() - 1000);
        assertThat(asset.getDownloadElapsedMillis()).isBetween(1000L, 5000L);
    }

    @Test
    void stoppedAttemptsUseTheirSavedDurationEvenWhileTheJobIsBeingPaused() {
        CrawlAsset asset = new CrawlAsset();
        asset.setStatus(CrawlAsset.Status.RUNNING);
        asset.setDownloadStartedAtMillis(System.currentTimeMillis() - 10_000);
        asset.setDownloadDurationMillis(250L);
        assertThat(asset.getDownloadElapsedMillis()).isEqualTo(250L);
    }

    @Test
    void legacyAndUnstartedAssetsDoNotInventADuration() {
        CrawlAsset asset = new CrawlAsset();
        assertThat(asset.getDownloadElapsedMillis()).isNull();
        asset.setStatus(CrawlAsset.Status.RUNNING);
        assertThat(asset.getDownloadElapsedMillis()).isNull();
    }
}
