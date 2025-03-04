package com.example.module2.serdes;

import com.example.module2.model.Message;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.common.serialization.Serializer;

@RequiredArgsConstructor
public class MessageSerializer implements Serializer<Message> {
    private final ObjectMapper objectMapper;

    @Override
    public byte[] serialize(String s, Message message) {
        if (message == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsBytes(message);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Ошибка сериализации объекта Message", e);
        }
    }
}
