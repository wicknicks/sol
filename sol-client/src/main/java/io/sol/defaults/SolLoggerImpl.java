package io.sol.defaults;

import io.sol.SolCapture;
import io.sol.SolLogger;

import java.util.HashMap;
import java.util.Map;

public class SolLoggerImpl implements SolLogger {

    final String name;

    public SolLoggerImpl(Class<?> klass) {
        this(klass.getName());
    }

    public SolLoggerImpl(String name) {
        this.name = name;
        SolMain.get().register(this);
    }

    public void log(String key, Object val) {
        Map<String, Object> event = new HashMap<>();
        event.put(key, val);
        log(event);
    }

    public void log(String key1, Object val1, String key2, Object val2) {
        Map<String, Object> event = new HashMap<>();
        event.put(key1, val1);
        event.put(key2, val2);
        log(event);
    }

    public void log(String key1, Object val1, String key2, Object val2, String key3, Object val3) {
        Map<String, Object> event = new HashMap<>();
        event.put(key1, val1);
        event.put(key2, val2);
        event.put(key3, val3);
        log(event);
    }

    public void log(String key1, Object val1, String key2, Object val2, String key3, Object val3, String key4, Object val4) {
        Map<String, Object> event = new HashMap<>();
        event.put(key1, val1);
        event.put(key2, val2);
        event.put(key3, val3);
        event.put(key4, val4);
        log(event);
    }

    public void log(String key1, Object val1, String key2, Object val2, String key3, Object val3, String key4, Object val4, String key5, Object val5) {
        Map<String, Object> event = new HashMap<>();
        event.put(key1, val1);
        event.put(key2, val2);
        event.put(key3, val3);
        event.put(key4, val4);
        event.put(key5, val5);
        log(event);
    }

    public void log(String key1, Object val1, String key2, Object val2, String key3, Object val3, String key4, Object val4, String key5, Object val5, String key6, Object val6) {
        Map<String, Object> event = new HashMap<>();
        event.put(key1, val1);
        event.put(key2, val2);
        event.put(key3, val3);
        event.put(key4, val4);
        event.put(key5, val5);
        event.put(key6, val6);
        log(event);
    }

    public void log(Map<String, Object> event) {
        SolMain.get().emitter().log(this.name, event);
    }

    public SolCapture capture() {
        return new SimpleCaptureImpl(this);
    }

    @Override
    public String toString() {
        return "SolLoggerImpl{" +
                "name='" + name + '\'' +
                '}';
    }
}
