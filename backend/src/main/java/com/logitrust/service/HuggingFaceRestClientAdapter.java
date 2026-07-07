package com.logitrust.service;

import java.util.List;
import java.util.Map;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** The only class in the codebase that touches RestClient's fluent API for Hugging Face calls. */
@Component
public class HuggingFaceRestClientAdapter implements HuggingFaceClient {

    private final RestClient huggingFaceRestClient;

    public HuggingFaceRestClientAdapter(RestClient huggingFaceRestClient) {
        this.huggingFaceRestClient = huggingFaceRestClient;
    }

    @Override
    public List<LabelScore> classify(String text, List<String> candidateLabels) {
        Map<String, Object> body = Map.of(
                "inputs", text,
                "parameters", Map.of("candidate_labels", candidateLabels));

        return huggingFaceRestClient.post()
                .body(body)
                .retrieve()
                .body(new ParameterizedTypeReference<List<LabelScore>>() {
                });
    }
}
