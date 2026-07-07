package com.logitrust.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class HuggingFaceConfig {

    private static final String DEFAULT_MODEL = "facebook/bart-large-mnli";

    @Bean
    public RestClient huggingFaceRestClient(HuggingFaceProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        int timeout = (int) Math.max(properties.timeoutMs(), 1000L);
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);

        String model = properties.model() != null && !properties.model().isBlank()
                ? properties.model()
                : DEFAULT_MODEL;

        RestClient.Builder builder = RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl("https://router.huggingface.co/hf-inference/models/" + model);

        if (properties.apiToken() != null && !properties.apiToken().isBlank()) {
            builder.defaultHeader("Authorization", "Bearer " + properties.apiToken());
        }

        return builder.build();
    }
}
