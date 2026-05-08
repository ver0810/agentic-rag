package com.agenticrag.infra.ai.service;

import com.agenticrag.infra.ai.model.AiRuntimeOptions;
import com.agenticrag.infra.ai.model.OpenAiRuntimeOptions;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class OpenAiCompatibleModelFactory {

    private final RestClient.Builder restClientBuilder;
    private final WebClient.Builder webClientBuilder;
    private final ResponseErrorHandler responseErrorHandler;
    private final RetryTemplate retryTemplate;
    private final ObservationRegistry observationRegistry;
    private final OpenAiChatModel defaultChatModel;

    public OpenAiCompatibleModelFactory(RestClient.Builder restClientBuilder,
                                        WebClient.Builder webClientBuilder,
                                        RetryTemplate retryTemplate,
                                        ObservationRegistry observationRegistry,
                                        OpenAiChatModel defaultChatModel) {
        this.restClientBuilder = restClientBuilder;
        this.webClientBuilder = webClientBuilder;
        this.retryTemplate = retryTemplate;
        this.observationRegistry = observationRegistry;
        this.defaultChatModel = defaultChatModel;
        this.responseErrorHandler = new DefaultResponseErrorHandler();
    }

    public OpenAiChatModel createChatModel(AiRuntimeOptions runtimeOptions, OpenAiChatOptions options) {
        return defaultChatModel.mutate()
                .openAiApi(buildOpenAiApi(cast(runtimeOptions)))
                .defaultOptions(options)
                .build();
    }

    public OpenAiEmbeddingModel createEmbeddingModel(AiRuntimeOptions runtimeOptions) {
        OpenAiRuntimeOptions openAiOptions = cast(runtimeOptions);
        OpenAiEmbeddingOptions options = new OpenAiEmbeddingOptions();
        if (openAiOptions.getEmbeddingModel() != null && !openAiOptions.getEmbeddingModel().isBlank()) {
            options.setModel(openAiOptions.getEmbeddingModel());
        }
        return new OpenAiEmbeddingModel(
                buildOpenAiApi(openAiOptions),
                MetadataMode.EMBED,
                options,
                retryTemplate,
                observationRegistry
        );
    }

    public OpenAiApi createApiClient(AiRuntimeOptions runtimeOptions) {
        return buildOpenAiApi(cast(runtimeOptions));
    }

    public List<AvailableModel> listModels(AiRuntimeOptions runtimeOptions) {
        Assert.notNull(runtimeOptions, "AI runtime options cannot be null");
        OpenAiRuntimeOptions openAiOptions = cast(runtimeOptions);
        Assert.hasText(openAiOptions.getBaseUrl(), "AI provider baseUrl cannot be empty");
        Assert.hasText(openAiOptions.getApiKey(), "AI provider apiKey cannot be empty");

        String modelsPath = StringUtils.hasText(openAiOptions.getModelsPath()) ? openAiOptions.getModelsPath() : "/v1/models";
        RestClient client = restClientBuilder
                .baseUrl(openAiOptions.getBaseUrl())
                .defaultHeader("Authorization", "Bearer " + openAiOptions.getApiKey())
                .build();
        ModelListResponse response = client.get()
                .uri(modelsPath)
                .retrieve()
                .body(ModelListResponse.class);
        if (response == null || response.data == null) {
            return List.of();
        }
        List<AvailableModel> models = new ArrayList<>();
        for (ModelItem item : response.data) {
            if (item == null || !StringUtils.hasText(item.id)) {
                continue;
            }
            AvailableModel model = new AvailableModel();
            model.setId(item.id);
            model.setDisplayName(StringUtils.hasText(item.name) ? item.name : item.id);
            if (item.architecture != null) {
                model.setInputModalities(item.architecture.input_modalities);
                model.setOutputModalities(item.architecture.output_modalities);
                model.setModality(item.architecture.modality);
            }
            models.add(model);
        }
        return models;
    }

    private OpenAiApi buildOpenAiApi(OpenAiRuntimeOptions runtimeOptions) {
        Assert.notNull(runtimeOptions, "AI runtime options cannot be null");
        Assert.hasText(runtimeOptions.getBaseUrl(), "AI provider baseUrl cannot be empty");
        Assert.hasText(runtimeOptions.getApiKey(), "AI provider apiKey cannot be empty");
        Assert.isTrue(!runtimeOptions.getApiKey().contains(" "), "AI provider apiKey format is invalid");

        OpenAiApi.Builder builder = OpenAiApi.builder()
                .baseUrl(runtimeOptions.getBaseUrl())
                .apiKey(runtimeOptions.getApiKey())
                .restClientBuilder(restClientBuilder)
                .webClientBuilder(webClientBuilder)
                .responseErrorHandler(responseErrorHandler);
        if (StringUtils.hasText(runtimeOptions.getCompletionsPath())) {
            builder.completionsPath(runtimeOptions.getCompletionsPath());
        }
        if (StringUtils.hasText(runtimeOptions.getEmbeddingsPath())) {
            builder.embeddingsPath(runtimeOptions.getEmbeddingsPath());
        }
        return builder.build();
    }

    private OpenAiRuntimeOptions cast(AiRuntimeOptions options) {
        if (options instanceof OpenAiRuntimeOptions) {
            return (OpenAiRuntimeOptions) options;
        }
        throw new IllegalArgumentException("OpenAiCompatibleModelFactory requires OpenAiRuntimeOptions");
    }

    public static class AvailableModel {
        private String id;
        private String displayName;
        private List<String> inputModalities = List.of();
        private List<String> outputModalities = List.of();
        private String modality;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getDisplayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }

        public List<String> getInputModalities() {
            return inputModalities;
        }

        public void setInputModalities(List<String> inputModalities) {
            this.inputModalities = inputModalities == null ? List.of() : inputModalities;
        }

        public List<String> getOutputModalities() {
            return outputModalities;
        }

        public void setOutputModalities(List<String> outputModalities) {
            this.outputModalities = outputModalities == null ? List.of() : outputModalities;
        }

        public String getModality() {
            return modality;
        }

        public void setModality(String modality) {
            this.modality = modality;
        }
    }

    private static class ModelListResponse {
        public List<ModelItem> data;
    }

    private static class ModelItem {
        public String id;
        public String name;
        public ModelArchitecture architecture;
        public Map<String, Object> top_provider;
    }

    private static class ModelArchitecture {
        public String modality;
        public List<String> input_modalities;
        public List<String> output_modalities;
    }
}
