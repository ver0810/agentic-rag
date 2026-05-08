package com.agenticrag.infra.ai.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AiChatProperties.class)
public class AiInfraAutoConfiguration {
}
