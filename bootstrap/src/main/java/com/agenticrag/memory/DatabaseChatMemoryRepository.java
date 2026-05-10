package com.agenticrag.memory;

import com.agenticrag.infra.ai.memory.ChatMemoryRepository;
import com.agenticrag.user.dao.entity.MessageDao;
import com.agenticrag.user.dao.mapper.MessageMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class DatabaseChatMemoryRepository implements ChatMemoryRepository {

    private final MessageMapper messageMapper;

    public DatabaseChatMemoryRepository(MessageMapper messageMapper) {
        this.messageMapper = messageMapper;
    }

    @Override
    public List<ChatMessage> getRecentMessages(String conversationId, int limit) {

        List<MessageDao> query = messageMapper.selectList(
                new LambdaQueryWrapper<MessageDao>()
                        .eq(MessageDao::getConversationId, conversationId)
                        .eq(MessageDao::getDeleted, 0)
                        .orderByDesc(MessageDao::getCreateTime)
                        .last("limit " + limit)
        );

        List<MessageDao> reversedMessage = new ArrayList<>(query);
        java.util.Collections.reverse(reversedMessage);

        return reversedMessage.stream()
                .map(d -> new ChatMessage(d.getRole(), d.getContent()))
                .collect(Collectors.toList());
    }
}
