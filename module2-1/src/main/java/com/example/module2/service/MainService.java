package com.example.module2.service;

import com.example.module2.config.Const;
import com.example.module2.config.ServiceProperties;
import com.example.module2.model.UserSet;
import com.example.module2.serdes.MessageSerdes;
import com.example.module2.serdes.UserSetSerdes;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.kstream.*;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Properties;

@Service
@Slf4j
public class MainService {
    private final ServiceProperties serviceProperties;
    private final MessageSerdes messageSerdes;
    private final UserSetSerdes userSetSerdes;
    private final KafkaStreams streams;


    public MainService(ServiceProperties serviceProperties,
                       MessageSerdes messageSerdes,
                       UserSetSerdes userSetSerdes) {
        this.serviceProperties = serviceProperties;
        this.messageSerdes = messageSerdes;
        this.userSetSerdes = userSetSerdes;
        this.streams = startStreams();
    }

    private KafkaStreams startStreams() {
        StreamsBuilder builder = new StreamsBuilder();

        /*
        таблица заблокированных пользователей
        ключ - получатель сообщения
        значение - множество заблокированных пользователей-отправителей
        * */
        KTable<String, UserSet> blockedUsersTable = builder.table(
            serviceProperties.getBlockedUsersTopic(),
            Consumed.with(Serdes.String(), userSetSerdes)
        );

        /*
        таблицыа запрещенныех слов
        ключ - запрещенное слово
        значение:
            - непустая строка - блокировка включена,
            - null - блокировка выключена
         используется в CensorshipProcessor
         */
        builder.globalTable(
            serviceProperties.getBlockedWordsTopic(),
            Consumed.with(Serdes.String(), Serdes.String()),
            Materialized.as(Const.CENSORED_WORDS_STORE)
        );

        /*
        поток сообщений
        ключ - получатель
        значение - Message (пользователь-отправитель и сообщение)
        * */
        builder.stream(serviceProperties.getInputMessageTopic(), Consumed.with(Serdes.String(), messageSerdes))
            // логируем исходное сообщение
            .peek((key, message) -> log.info("input: ({}, {})", key, message))
            // применяем фильтр заблокированный пользователей
            .leftJoin(blockedUsersTable, (toUser, message, blockedUserSet) -> {
                if (blockedUserSet != null
                    && blockedUserSet.users().contains(message.getFromUser())) {
                    log.info("удалено сообщение: ({}, {})", toUser, message);
                    return null;
                }
                return message;
            })
            .filter((key, message) -> message != null)
            // удаленные сообщения не доходят до этого места
            // обработка текста сообщения
            .processValues(CensorshipProcessor::new)
            // логируем обработанное сообщение
            .peek((key, message) -> log.info("output: ({}, {})", key, message))
            // сохраняем результат в выходной топик
            .to(serviceProperties.getOutputMessageTopic(), Produced.with(Serdes.String(), messageSerdes));

        // Старт потока
        var streams = new KafkaStreams(builder.build(), getProperties());
        streams.start();
        return streams;
    }

    private Properties getProperties() {
        Properties properties = new Properties();
        properties.put(StreamsConfig.APPLICATION_ID_CONFIG, "module2-1");
        properties.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, serviceProperties.getBoostrapServersConfig());
        return properties;
    }

    @EventListener(ContextClosedEvent.class)
    private void onContextClosedEvent(ContextClosedEvent contextClosedEvent) {
        streams.close(Duration.ofSeconds(10));
    }
}
