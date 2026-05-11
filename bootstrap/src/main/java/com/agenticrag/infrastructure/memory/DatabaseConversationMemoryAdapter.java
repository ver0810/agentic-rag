package com.agenticrag.infrastructure.memory;

import com.agenticrag.infra.ai.port.memory.ConversationMemoryPort;
import com.agenticrag.user.dao.entity.MessageEntity;
import com.agenticrag.user.dao.mapper.MessageMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class DatabaseConversationMemoryAdapter implements ConversationMemoryPort {

    private final MessageMapper messageMapper;

    public DatabaseConversationMemoryAdapter(MessageMapper messageMapper) {
        this.messageMapper = messageMapper;
    }

    @Override
    public List<MemoryMessageView> getRecentMessages(String conversationId, int limit) {
        List<MessageEntity> query = messageMapper.selectList(
                new LambdaQueryWrapper<MessageEntity>()
                        .eq(MessageEntity::getConversationId, conversationId)
                        .eq(MessageEntity::getDeleted, 0)
                        .orderByDesc(MessageEntity::getCreateTime)
                        .last("limit " + limit)
        );

        List<MessageEntity> reversedMessages = new ArrayList<>(query);
        java.util.Collections.reverse(reversedMessages);

        return reversedMessages.stream()
                .map(item -> new MemoryMessageView(item.getRole(), item.getContent()))
                .collect(Collectors.toList());
    }

    private record MemoryMessageView(String role, String content) implements MemoryMessage {}
}
