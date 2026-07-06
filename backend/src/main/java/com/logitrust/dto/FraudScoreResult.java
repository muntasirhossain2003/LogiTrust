package com.logitrust.dto;

import java.util.List;

public record FraudScoreResult(int totalScore, List<ScoringFactorResult> factors) {
}
