package com.example.module2.serdes;

import com.example.module2.model.UserSet;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.common.serialization.Deserializer;

@RequiredArgsConstructor
public class UserSetDeserializer implements Deserializer<UserSet> {
    private final ObjectMapper objectMapper;

    @Override
    public UserSet deserialize(String s, byte[] data) {
        if (data == null) {
            return null;
        }
        try {
            var userArray = objectMapper.readValue(data, String[].class);
            return new UserSet(userArray);
        } catch (Exception e) {
            throw new RuntimeException("Ошибка десериализации объекта User", e);
        }
    }
}
