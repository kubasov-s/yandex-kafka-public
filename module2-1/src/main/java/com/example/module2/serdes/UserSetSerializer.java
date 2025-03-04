package com.example.module2.serdes;

import com.example.module2.model.UserSet;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.common.serialization.Serializer;

@RequiredArgsConstructor
public class UserSetSerializer implements Serializer<UserSet> {
    private final ObjectMapper objectMapper;

    @Override
    public byte[] serialize(String topic, UserSet userSet) {
        if (userSet == null) {
            return null;
        }

        var userArray = userSet.users().toArray();
        try {
            return objectMapper.writeValueAsBytes(userArray);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Ошибка сериализации объекта Message", e);
        }
    }
}
