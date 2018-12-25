package io.sol.defaults;

import io.sol.SolCapture;
import io.sol.SolLogger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SimpleCaptureImpl implements SolCapture {

    private final SolLogger parent;
    private final Map<String, Object> holder = new ConcurrentHashMap<>();

    SimpleCaptureImpl(SolLogger parent) {
        this.parent = parent;
    }

    @Override
    public void put(String key, Object val) {
        holder.put(key, val);
    }

    @Override
    public void close() {
        this.parent.log(holder);
    }

}
