package org.example.module3;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.config.AbstractConfig;
import org.apache.kafka.connect.sink.SinkRecord;
import org.apache.kafka.connect.sink.SinkTask;
import org.example.module3.model.MetricEvent;
import org.example.module3.utils.Dump;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;
import java.util.Map;

@Slf4j
public class PrometheusSinkTask extends SinkTask {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    private PrometheusHttpServer httpServer;


    @Override
    public String version() {
        return "1.0.0";
    }

    @Override
    public void start(Map<String, String> props) {
        try {
            Dump.dump(log, "start", props);
            if (httpServer != null) {
                throw new RuntimeException("http server is already running");
            }
            AbstractConfig config = new AbstractConfig(PrometheusSinkConnector.CONFIG_DEF, props);
            URL url = getUrl(config);
            try {
                httpServer = new PrometheusHttpServer();
                httpServer.start(url);
            } catch (Exception ex) {
                httpServer = null;
                throw new RuntimeException("failed to start http server", ex);
            }
        } catch (Exception e) {
            log.error("failed to start task", e);
            throw e;
        }
    }

    private URL getUrl(AbstractConfig config) {
        String urlStr = config.getString(Const.PROMETHEUS_LISTENER_URL_CONFIG);
        if (urlStr == null) {
            throw new RuntimeException("prometheus url is not specified");
        }
        try {
            return new URL(urlStr);
        } catch (MalformedURLException e) {
            throw new RuntimeException(String.format("malformed prometheus url: %s", urlStr), e);
        }
    }

    @Override
    public void put(Collection<SinkRecord> records) {
        Dump.dump(log, "put", records);
        if (httpServer == null) {
            return;
        }
        for (var record : records) {
            var value = record.value();
            if (value == null) {
                continue;
            }
            try {
                var metricMap = MAPPER.readValue(value.toString(), new TypeReference<Map<String, MetricEvent>>() {});
                for (var entity: metricMap.values()) {
                    httpServer.add(entity);
                }
            } catch (JsonProcessingException e) {
                log.error("invalid message {}", value, e);
            }
        }
    }

    @Override
    public void stop() {
        log.debug("stop");
        try {
            stopImpl();
        } catch (Exception ex) {
            log.error("failed to stop task", ex);
            throw new RuntimeException(ex);
        }
    }

    private void stopImpl() throws Exception {
        if (httpServer != null) {
            try {
                httpServer.stop();
            } finally {
                httpServer = null;
            }
        }
    }
}
