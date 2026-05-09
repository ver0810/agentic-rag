package com.agenticrag.infra.ai.service;

import com.agenticrag.infra.ai.config.AiProviderProperties;
import com.agenticrag.infra.ai.model.AiRuntimeOptions;
import com.agenticrag.infra.ai.model.OpenAiRuntimeOptions;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class OpenAiModelFactory implements AiModelFactory {

    private final RestClient.Builder restClientBuilder;
    private final WebClient.Builder webClientBuilder;
    private final ResponseErrorHandler responseErrorHandler;
    private final RetryTemplate retryTemplate;
    private final ObservationRegistry observationRegistry;
    private final OpenAiChatModel defaultChatModel;

    public OpenAiModelFactory(RestClient.Builder restClientBuilder,
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

    public static OpenAiRuntimeOptions fromProviderConfig(AiProviderProperties.ProviderConfig config) {
        OpenAiRuntimeOptions options = new OpenAiRuntimeOptions();
        options.setBaseUrl(config.getBaseUrl());
        options.setApiKey(config.getApiKey());
        options.setChatModel(config.getChatModel());
        options.setEmbeddingModel(config.getEmbeddingModel());
        options.setCompletionsPath(config.getCompletionsPath());
        options.setEmbeddingsPath(config.getEmbeddingsPath());
        options.setModelsPath(config.getModelsPath());
        return options;
    }

    @Override
    public boolean supports(String provider) {
        if (provider == null || provider.isBlank()) {
            return true;
        }
        String lower = provider.toLowerCase();
        return "openai".equals(lower) 
                || "deepseek".equals(lower)
                || "qwen".equals(lower)
                || "moonshot".equals(lower)
                || "zhipu".equals(lower)
                || "siliconflow".equals(lower)
                || "openrouter".equals(lower)
                || "groq".equals(lower);
    }

    @Override
    public String getProvider() {
        return "openai";
    }

    @Override
    public ChatModel createChatModel(AiRuntimeOptions options, ChatOptions chatOptions) {
        OpenAiRuntimeOptions openAiOptions = cast(options);
        OpenAiChatOptions openAiChatOptions = chatOptions instanceof OpenAiChatOptions 
            ? (OpenAiChatOptions) chatOptions 
            : OpenAiChatOptions.builder().build();
        
        return defaultChatModel.mutate()
                .openAiApi(buildOpenAiApi(openAiOptions))
                .defaultOptions(openAiChatOptions)
                .build();
    }

    @Override
    public EmbeddingModel createEmbeddingModel(AiRuntimeOptions options) {
        OpenAiRuntimeOptions openAiOptions = cast(options);
        OpenAiEmbeddingOptions embeddingOptions = new OpenAiEmbeddingOptions();
        if (openAiOptions.getEmbeddingModel() != null && !openAiOptions.getEmbeddingModel().isBlank()) {
            embeddingOptions.setModel(openAiOptions.getEmbeddingModel());
        }
        return new OpenAiEmbeddingModel(
                buildOpenAiApi(openAiOptions),
                MetadataMode.EMBED,
                embeddingOptions,
                retryTemplate,
                observationRegistry
        );
    }

    public OpenAiApi createApiClient(AiRuntimeOptions options) {
        return buildOpenAiApi(cast(options));
    }

    public List<AvailableModel> listModels(AiRuntimeOptions options) {
        Assert.notNull(options, "AI runtime options cannot be null");
        OpenAiRuntimeOptions openAiOptions = cast(options);
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
        throw new IllegalArgumentException("OpenAiModelFactory requires OpenAiRuntimeOptions");
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