package com.example.module2.service;

import com.example.module2.config.Const;
import com.example.module2.model.Message;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.streams.processor.api.FixedKeyProcessor;
import org.apache.kafka.streams.processor.api.FixedKeyProcessorContext;
import org.apache.kafka.streams.processor.api.FixedKeyRecord;
import org.apache.kafka.streams.state.KeyValueStore;

import java.util.regex.Pattern;

/* обработка текст сообщения - замена запрещенных слов
* */
@Slf4j
class CensorshipProcessor implements FixedKeyProcessor<String, Message, Message> {
    private static final Pattern wordPattern = Pattern.compile("\\w+");
    private KeyValueStore<String, String> blockedWordsStateStore;
    private FixedKeyProcessorContext<String, Message> context;

    @Override
    public void init(FixedKeyProcessorContext<String, Message> context) {
        this.context = context;
        blockedWordsStateStore = context.getStateStore(Const.CENSORED_WORDS_STORE);
    }

    @Override
    public void process(FixedKeyRecord<String, Message> record) {
        String message = record.value().getMessage();
        log.info("process message: {}", message);
        // находим слова в тексте сообщения
        String censoredMessage = wordPattern.matcher(message).replaceAll(m -> {
            var word = m.group(0);
            // если слово в списке заблокированных, возвращаем замещающий текст
            if (blockedWordsStateStore.get(word) != null) {
                return Const.CENSORED_TEXT;
            }
            // иначе возврщаемся исходное слово
            return word;
        });
        // обновляем сообщение
        record.value().setMessage(censoredMessage);

        // send the record down the stream
        context.forward(record);
    }
}
