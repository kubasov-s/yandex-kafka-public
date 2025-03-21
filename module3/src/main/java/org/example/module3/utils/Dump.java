package org.example.module3.utils;

import java.util.Collection;
import java.util.Map;

import org.apache.kafka.connect.sink.SinkRecord;
import org.slf4j.Logger;

public class Dump {
    public static void dump(Logger log, String prefix, Map<String, String> props) {
        if (!log.isDebugEnabled()) {
            return;
        }
        var sb = new StringBuilder();
        if (props != null) {
            for (var entry : props.entrySet()) {
                sb.append(String.format("%s: %s\n", entry.getKey(), entry.getValue()));
            }
        }
        log.debug("{} props:\n{}", prefix, sb);
    }

    public static void dump(Logger log, String prefix, Collection<SinkRecord> records) {
        if (!log.isDebugEnabled()) {
            return;
        }
        var sb = new StringBuilder();
        for (var record : records) {
            sb.append(record.value());
            sb.append("\n");
        }
        log.debug("{} size: {}\n{}", prefix, records.size(), sb);
    }
}
