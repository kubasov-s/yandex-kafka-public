package com.example.module2.serdes;

import com.example.module2.model.Message;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.common.serialization.Deserializer;

@RequiredArgsConstructor
public class MessageDeserializer implements Deserializer<Message> {
    private final ObjectMapper objectMapper;

    @Override
    public Message deserialize(String topic, byte[] data) {
        if (data == null) {
            return null;
        }
        try {
            return objectMapper.readValue(data, Message.class);
        } catch (Exception e) {
            return null;
        }
    }
}
