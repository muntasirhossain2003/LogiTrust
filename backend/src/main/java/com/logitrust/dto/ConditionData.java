package com.logitrust.dto;

/** Optional temperature/humidity reading logged at a checkpoint (FR-2.4). */
public record ConditionData(Double temperatureC, Double humidityPct) {
}
