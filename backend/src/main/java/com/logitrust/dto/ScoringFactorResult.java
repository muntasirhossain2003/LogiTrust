package com.logitrust.dto;

/** One named contribution to a fraud score, always carrying a human explanation (FR-4.5). */
public record ScoringFactorResult(String name, int score, double weight, String explanation) {

    public double contribution() {
        return score * weight;
    }
}
