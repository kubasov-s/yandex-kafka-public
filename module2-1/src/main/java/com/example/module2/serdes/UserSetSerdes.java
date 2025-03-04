package com.example.module2.serdes;

import com.example.module2.model.UserSet;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.common.serialization.Serdes;

public class UserSetSerdes extends Serdes.WrapperSerde<UserSet> {
    public UserSetSerdes(ObjectMapper objectMapper) {
        super(new UserSetSerializer(objectMapper), new UserSetDeserializer(objectMapper));
    }
}
