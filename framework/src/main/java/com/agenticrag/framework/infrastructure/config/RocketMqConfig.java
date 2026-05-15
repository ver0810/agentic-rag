package com.agenticrag.framework.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RocketMqConfig {

    @Bean
    @ConfigurationProperties(prefix = "agenticrag.event.ingestion")
    public EventBindingProperties ingestionEventBinding() {
        return new EventBindingProperties();
    }

    @Bean
    @ConfigurationProperties(prefix = "agenticrag.event.evaluation")
    public EventBindingProperties evaluationEventBinding() {
        return new EventBindingProperties();
    }

    public static class EventBindingProperties {
        private String topic;
        private String consumerGroup;

        public String getTopic() { return topic; }
        public void setTopic(String topic) { this.topic = topic; }
        public String getConsumerGroup() { return consumerGroup; }
        public void setConsumerGroup(String consumerGroup) { this.consumerGroup = consumerGroup; }
    }
}
