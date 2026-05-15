package com.agenticrag.framework.infrastructure.mq;

import com.agenticrag.framework.infrastructure.config.RocketMqConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
public class RocketMqEventPublisherAdapter implements EventPublisherPort {

    private final RocketMQTemplate rocketMQTemplate;
    private final RocketMqConfig.EventBindingProperties ingestionBinding;
    private final ObjectMapper objectMapper;

    public RocketMqEventPublisherAdapter(RocketMQTemplate rocketMQTemplate,
                                          @Qualifier("ingestionEventBinding") RocketMqConfig.EventBindingProperties ingestionBinding,
                                          ObjectMapper objectMapper) {
        this.rocketMQTemplate = rocketMQTemplate;
        this.ingestionBinding = ingestionBinding;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publishIngestionEvent(String docId, String taskId, String userId) {
        MqEvent event = MqEvent.of("ingestion", Map.of("docId", docId, "taskId", taskId, "userId", userId));
        try {
            String json = objectMapper.writeValueAsString(event);
            rocketMQTemplate.syncSend(ingestionBinding.getTopic(), json);
            log.info("Ingestion event published: docId={}, taskId={}", docId, taskId);
        } catch (Exception e) {
            log.error("Failed to publish ingestion event: docId={}", docId, e);
        }
    }
}
