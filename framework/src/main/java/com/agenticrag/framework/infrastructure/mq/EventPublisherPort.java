package com.agenticrag.framework.infrastructure.mq;

public interface EventPublisherPort {

    void publishIngestionEvent(String docId, String taskId, String userId);
}
