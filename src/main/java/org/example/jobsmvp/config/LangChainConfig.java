package org.example.jobsmvp.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.googleai.GoogleAiEmbeddingModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

import static dev.langchain4j.model.chat.Capability.RESPONSE_FORMAT_JSON_SCHEMA;

@Configuration
public class LangChainConfig {


    @Value("${langchain4j.google-ai-gemini.api-key}")
    private String geminiAiApiKey;

    @Value("${langchain4j.google-ai-gemini.chat-model.model-name:gemini-1.5-flash}")
    private String chatModelName;

    @Value("${langchain4j.google-ai-gemini.embedding-model.model-name:text-embedding-001}")
    private String embeddingModelName;

    @Value("${langchain4j.google-ai-gemini.chat-model.temperature:0.0}")
    private double chatModelTemperature;

    @Bean
    public ChatModel chatModel() {
        return GoogleAiGeminiChatModel.builder()
                .apiKey(geminiAiApiKey)
                .modelName(chatModelName)
                .temperature(chatModelTemperature)
                .supportedCapabilities(RESPONSE_FORMAT_JSON_SCHEMA)
                .timeout(Duration.ofMinutes(3))
                .build();
    }

    @Bean
    public EmbeddingModel embeddingModel() {
        return GoogleAiEmbeddingModel.builder()
                .apiKey(geminiAiApiKey)
                .modelName(embeddingModelName)
                .outputDimensionality(768)
                .timeout(Duration.ofSeconds(60))
                .build();
    }


}