package org.example.module3;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.config.AbstractConfig;
import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.connect.connector.Task;
import org.apache.kafka.connect.sink.SinkConnector;
import org.example.module3.utils.Dump;

import java.util.List;
import java.util.Map;

@Slf4j
public class PrometheusSinkConnector extends SinkConnector {
    static final ConfigDef CONFIG_DEF = new ConfigDef()
        .define(Const.PROMETHEUS_LISTENER_URL_CONFIG, ConfigDef.Type.STRING, Const.DEFAULT_URL, ConfigDef.Importance.HIGH,
                "url exposing metrics in prometheus format");
    /**
     * task's config
     * this property is non-null when connector is started and null otherwise.
     */
    private Map<String, String> props;

    @Override
    public String version() {
        return "1.0.0";
    }

    @Override
    public void start(Map<String, String> props) {
        Dump.dump(log, "start", props);
        this.props = props;
    }

    @Override
    public Class<? extends Task> taskClass() {
        return PrometheusSinkTask.class;
    }

    @Override
    public List<Map<String, String>> taskConfigs(int maxTasks) {
        log.debug("taskConfigs maxTasks: {}, props {} null", maxTasks, props == null ? "==" : "!=");
        if (maxTasks < 1) {
            return List.of();
        }
        if (props == null) {
            return List.of();
        }
        // at most one task
        Dump.dump(log, "taskConfigs", props);
        return List.of(props);
    }

    @Override
    public void stop() {
        log.debug("stop");
        props = null;
    }

    @Override
    public ConfigDef config() {
        return CONFIG_DEF;
    }

    @Override
    public boolean alterOffsets(Map<String, String> connectorConfig, Map<TopicPartition, Long> offsets) {
        // Nothing to do here since PrometheusSinkConnector does not manage offsets externally nor does it require any
        // custom offset validation
        return true;
    }
}
