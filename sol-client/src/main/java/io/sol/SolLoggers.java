package io.sol;

import io.sol.defaults.SolLoggerImpl;

import java.util.concurrent.ConcurrentHashMap;

public class SolLoggers {

    static ConcurrentHashMap<Class<?>, SolLogger> loggers = new ConcurrentHashMap<>();

    public static SolLogger logger(Class<?> klass) {
        return loggers.computeIfAbsent(klass, SolLoggerImpl::new);
    }
}
