package com.logitrust.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.logitrust.config.HuggingFaceProperties;
import com.logitrust.dto.ScoringFactorResult;
import java.util.List;
import org.junit.jupiter.api.Test;

class HuggingFaceRiskServiceTest {

    @Test
    void noIncidentNote_scoresZero_withoutCallingTheApi() {
        HuggingFaceClient client = mock(HuggingFaceClient.class);
        HuggingFaceRiskService service = new HuggingFaceRiskService(
                client, new HuggingFaceProperties("hf_faketoken", "facebook/bart-large-mnli", 3000));

        ScoringFactorResult result = service.scoreIncidentNote(null, 0.13);

        assertThat(result.score()).isZero();
        assertThat(result.name()).isEqualTo("incidentTextRisk");
    }

    @Test
    void blankApiToken_scoresZero_withoutCallingTheApi() {
        HuggingFaceClient client = mock(HuggingFaceClient.class);
        HuggingFaceRiskService service = new HuggingFaceRiskService(
                client, new HuggingFaceProperties("", "facebook/bart-large-mnli", 3000));

        ScoringFactorResult result = service.scoreIncidentNote("box was wet and torn open", 0.13);

        assertThat(result.score()).isZero();
        assertThat(result.explanation()).contains("not configured");
    }

    @Test
    void classifierConfidentItIsNormal_scoresLow() {
        HuggingFaceClient client = mock(HuggingFaceClient.class);
        List<HuggingFaceClient.LabelScore> response = List.of(
                new HuggingFaceClient.LabelScore(HuggingFaceRiskService.NORMAL_LABEL, 0.9),
                new HuggingFaceClient.LabelScore("package tampered or damaged", 0.07),
                new HuggingFaceClient.LabelScore("counterfeit or suspicious activity", 0.03));
        when(client.classify(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(response);

        HuggingFaceRiskService service = new HuggingFaceRiskService(
                client, new HuggingFaceProperties("hf_faketoken", "facebook/bart-large-mnli", 3000));

        ScoringFactorResult result = service.scoreIncidentNote("delivered on time, box looked fine", 0.13);

        assertThat(result.score()).isEqualTo(10); // round((1 - 0.9) * 100)
    }

    @Test
    void classifierConfidentOfTampering_scoresHigh() {
        HuggingFaceClient client = mock(HuggingFaceClient.class);
        List<HuggingFaceClient.LabelScore> response = List.of(
                new HuggingFaceClient.LabelScore("package tampered or damaged", 0.85),
                new HuggingFaceClient.LabelScore("counterfeit or suspicious activity", 0.10),
                new HuggingFaceClient.LabelScore(HuggingFaceRiskService.NORMAL_LABEL, 0.05));
        when(client.classify(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(response);

        HuggingFaceRiskService service = new HuggingFaceRiskService(
                client, new HuggingFaceProperties("hf_faketoken", "facebook/bart-large-mnli", 3000));

        ScoringFactorResult result = service.scoreIncidentNote("box was wet, seal looked broken", 0.13);

        assertThat(result.score()).isEqualTo(95); // round((1 - 0.05) * 100)
        assertThat(result.explanation()).contains("package tampered or damaged");
    }

    @Test
    void apiThrows_failsSoft_scoresZero() {
        HuggingFaceClient client = mock(HuggingFaceClient.class);
        when(client.classify(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyList()))
                .thenThrow(new RuntimeException("connection timed out"));

        HuggingFaceRiskService service = new HuggingFaceRiskService(
                client, new HuggingFaceProperties("hf_faketoken", "facebook/bart-large-mnli", 3000));

        ScoringFactorResult result = service.scoreIncidentNote("box was wet", 0.13);

        assertThat(result.score()).isZero();
        assertThat(result.explanation()).contains("unavailable");
    }
}
