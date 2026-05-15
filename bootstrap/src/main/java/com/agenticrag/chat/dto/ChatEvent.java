package com.agenticrag.chat.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatEvent(
        String type,
        Object data
) {
    public static ChatEvent chunk(String content) {
        return new ChatEvent("chunk", content);
    }

    public static ChatEvent metadata(Object metadata) {
        return new ChatEvent("metadata", metadata);
    }

    public static ChatEvent error(String message) {
        return new ChatEvent("error", message);
    }

    public static ChatEvent done() {
        return new ChatEvent("done", null);
    }
}
