package com.logitrust.service;

import java.util.List;

/**
 * Thin transport boundary around the Hugging Face Inference API. Isolated
 * from {@link HuggingFaceRiskService} on purpose: Spring's RestClient
 * fluent builder chain is a third-party generic interface hierarchy that is
 * notoriously unreliable to mock directly in unit tests (bridge methods
 * across its interface hierarchy can make a stub registered on one overload
 * silently miss the real invocation). Wrapping it behind this single-method
 * interface keeps the scoring logic trivially testable and keeps the
 * RestClient-specific code in one small, integration-tested place.
 */
public interface HuggingFaceClient {

    List<LabelScore> classify(String text, List<String> candidateLabels);

    record LabelScore(String label, double score) {
    }
}
