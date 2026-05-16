package com.agenticrag.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.support.RetryTemplate;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class ExternalApiConfiguration {

    @Bean
    public HttpClient externalHttpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Bean
    public RetryTemplate externalApiRetryTemplate() {
        return RetryTemplate.builder()
                .maxAttempts(3)
                .fixedBackoff(Duration.ofSeconds(1))
                .retryOn(java.io.IOException.class)
                .retryOn(java.net.http.HttpTimeoutException.class)
                .build();
    }
}