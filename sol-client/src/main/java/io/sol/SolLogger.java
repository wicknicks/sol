package io.sol;

import java.util.Map;

public interface SolLogger {

    void log(String key, Object val);

    void log(String key1, Object val1, String key2, Object val2);

    void log(String key1, Object val1, String key2, Object val2, String key3, Object val3);

    void log(String key1, Object val1, String key2, Object val2, String key3, Object val3, String key4, Object val4);

    void log(String key1, Object val1, String key2, Object val2, String key3, Object val3, String key4, Object val4, String key5, Object val5);

    void log(String key1, Object val1, String key2, Object val2, String key3, Object val3, String key4, Object val4, String key5, Object val5, String key6, Object val6);

    void log(Map<String, Object> event);

    SolCapture capture();

}
