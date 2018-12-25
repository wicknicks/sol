package io.sol;

import org.apache.kafka.common.Configurable;

import java.util.Map;

public interface EventEmitter extends Configurable {

    default void register(Map<String, Object> registration) {
        // do nothing
    }

    default void log(String name, Map<String, Object> event) {
        // do nothing
    }

    default
    void configure(Map<String, ?> configs) {
        // do nothing
    }

}
