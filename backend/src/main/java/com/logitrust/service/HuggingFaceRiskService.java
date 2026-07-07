package com.logitrust.service;

import com.logitrust.config.HuggingFaceProperties;
import com.logitrust.dto.ScoringFactorResult;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Sixth fraud factor: zero-shot text classification of a courier's free-text
 * incident note via Hugging Face's free Inference API (facebook/bart-large-mnli
 * by default). This is a genuinely pretrained deep-learning model — but it was
 * never trained on shipping data or fraud labels; "zero-shot" means we hand it
 * our own candidate labels at call time and it scores the text against them
 * using general language understanding. No training happens here, ever.
 *
 * Deliberately fails soft: a network hiccup, rate limit, or missing API token
 * must never block checkpoint logging. Any failure — including no token
 * configured at all — falls back to a neutral 0 with an explanation, so the
 * five rule-based factors keep working with zero external dependency.
 */
@Service
public class HuggingFaceRiskService {

    private static final Logger log = LoggerFactory.getLogger(HuggingFaceRiskService.class);

    static final String NORMAL_LABEL = "shipment appears normal, no issue";
    private static final List<String> CANDIDATE_LABELS = List.of(
            "package tampered or damaged",
            "counterfeit or suspicious activity",
            NORMAL_LABEL);

    private final HuggingFaceClient huggingFaceClient;
    private final HuggingFaceProperties properties;

    public HuggingFaceRiskService(HuggingFaceClient huggingFaceClient, HuggingFaceProperties properties) {
        this.huggingFaceClient = huggingFaceClient;
        this.properties = properties;
    }

    public ScoringFactorResult scoreIncidentNote(String incidentNote, double weight) {
        if (incidentNote == null || incidentNote.isBlank()) {
            return new ScoringFactorResult("incidentTextRisk", 0, weight, "No incident note supplied.");
        }
        if (properties.apiToken() == null || properties.apiToken().isBlank()) {
            return new ScoringFactorResult("incidentTextRisk", 0, weight,
                    "Hugging Face API token not configured - text risk classification skipped.");
        }

        try {
            List<HuggingFaceClient.LabelScore> scores = huggingFaceClient.classify(incidentNote, CANDIDATE_LABELS);

            if (scores == null || scores.isEmpty()) {
                return new ScoringFactorResult("incidentTextRisk", 0, weight, "Classifier returned no result.");
            }

            double normalScore = scores.stream()
                    .filter(s -> NORMAL_LABEL.equals(s.label()))
                    .findFirst()
                    .map(HuggingFaceClient.LabelScore::score)
                    .orElse(0.0);
            int score = (int) Math.round(Math.max(0, Math.min(1, 1 - normalScore)) * 100);

            String explanation = String.format(
                    "Hugging Face zero-shot classifier rated \"%s\" as most likely for this note "
                            + "(%.0f%% confidence it's normal).",
                    scores.get(0).label(), normalScore * 100);
            return new ScoringFactorResult("incidentTextRisk", score, weight, explanation);
        } catch (Exception e) {
            log.warn("Hugging Face risk classification failed, skipping this factor: {}", e.getMessage());
            return new ScoringFactorResult("incidentTextRisk", 0, weight,
                    "Text risk classification temporarily unavailable - not evaluated.");
        }
    }
}
