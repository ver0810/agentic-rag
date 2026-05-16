package com.agenticrag.feedback;

import com.agenticrag.feedback.dao.mapper.FeedbackMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class FeedbackService {

    private final FeedbackMapper feedbackMapper;

    public FeedbackService(FeedbackMapper feedbackMapper) {
        this.feedbackMapper = feedbackMapper;
    }

    public FeedbackEntity submit(FeedbackRequest request, String userId) {
        FeedbackEntity entity = new FeedbackEntity();
        entity.setTraceId(request.traceId());
        entity.setKbId(request.kbId());
        entity.setUserId(userId);
        entity.setQuery(request.query());
        entity.setAnswer(request.answer());
        entity.setRating(request.rating());
        entity.setComment(request.comment());
        entity.setCreateTime(LocalDateTime.now());
        entity.setUpdateTime(LocalDateTime.now());
        entity.setDeleted(0);
        feedbackMapper.insert(entity);
        return entity;
    }

    public List<FeedbackEntity> list(String kbId, Integer limit, String userId) {
        LambdaQueryWrapper<FeedbackEntity> wrapper = new LambdaQueryWrapper<FeedbackEntity>()
                .eq(FeedbackEntity::getDeleted, 0)
                .eq(userId != null, FeedbackEntity::getUserId, userId)
                .orderByDesc(FeedbackEntity::getCreateTime)
                .last(true, "limit " + Math.max(1, Math.min(limit != null ? limit : 50, 200)));
        if (kbId != null) {
            wrapper.eq(FeedbackEntity::getKbId, kbId);
        }
        return feedbackMapper.selectList(wrapper);
    }

    public FeedbackSummaryDTO getSummary(String kbId, String userId) {
        List<FeedbackEntity> all = feedbackMapper.selectList(
                new LambdaQueryWrapper<FeedbackEntity>()
                        .eq(FeedbackEntity::getDeleted, 0)
                        .eq(userId != null, FeedbackEntity::getUserId, userId)
                        .eq(kbId != null, FeedbackEntity::getKbId, kbId));
        if (all.isEmpty()) {
            return new FeedbackSummaryDTO(0, 0L, 0L, 0L, 0.0);
        }
        long positive = all.stream().filter(f -> f.getRating() != null && f.getRating() >= 4).count();
        long neutral = all.stream().filter(f -> f.getRating() != null && f.getRating() == 3).count();
        long negative = all.stream().filter(f -> f.getRating() != null && f.getRating() <= 2).count();
        double avgRating = all.stream()
                .filter(f -> f.getRating() != null)
                .mapToInt(FeedbackEntity::getRating)
                .average()
                .orElse(0.0);
        return new FeedbackSummaryDTO(all.size(), positive, neutral, negative, avgRating);
    }

    public record FeedbackSummaryDTO(
            int total,
            long positive,
            long neutral,
            long negative,
            double avgRating
    ) {}
}
