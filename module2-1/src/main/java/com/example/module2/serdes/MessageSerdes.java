package com.example.module2.serdes;

import com.example.module2.model.Message;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.common.serialization.Serdes;

public class MessageSerdes extends Serdes.WrapperSerde<Message> {
    public MessageSerdes(ObjectMapper objectMapper) {
        super(new MessageSerializer(objectMapper), new MessageDeserializer(objectMapper));
    }
}
