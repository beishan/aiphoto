package com.aiphoto.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CrawlJobPriorityTest {
    @Test
    void defaultPriorityFollowsTheTaskPhaseIncludingLegacyJobs() {
        CrawlJob job = new CrawlJob();
        assertThat(job.getPriority()).isEqualTo(100);
        job.setPhase(CrawlJob.Phase.DOWNLOAD);
        assertThat(job.getPriority()).isEqualTo(75);
    }

    @Test
    void explicitPrioritySurvivesPhaseChangesAndCanBeReset() {
        CrawlJob job = new CrawlJob();
        job.setPriority(25);
        job.setPhase(CrawlJob.Phase.DOWNLOAD);
        assertThat(job.getPriority()).isEqualTo(25);
        job.setPriority(null);
        assertThat(job.getPriority()).isEqualTo(75);
    }
}
