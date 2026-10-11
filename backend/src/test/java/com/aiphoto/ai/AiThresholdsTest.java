package com.aiphoto.ai;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;

class AiThresholdsTest {
    @Test
    void convertsSimilarityInsteadOfRelaxingHigherThresholds() {
        assertThat(AiThresholds.distance(AiThresholds.similarity("80", 0.8))).isCloseTo(0.2, within(1e-8));
        assertThat(AiThresholds.distance(1)).isZero();
        assertThat(AiThresholds.similarity("NaN", 0.5)).isEqualTo(0.5);
        assertThat(AiThresholds.similarity("120", 0.5)).isEqualTo(0.5);
        assertThatThrownBy(() -> AiThresholds.distance(Double.NaN)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void mapsLegacyObjectTagsAndAvoidsTreatingTablewareAsFood() {
        assertThat(YoloCategories.icon("cat", "object")).isEqualTo("animal");
        assertThat(YoloCategories.icon("person", "object")).isEqualTo("person");
        assertThat(YoloCategories.icon("banana", "object")).isEqualTo("food");
        assertThat(YoloCategories.icon("potted plant", "object")).isEqualTo("plant");
        assertThat(YoloCategories.icon("cup", "object")).isNull();
    }
}
