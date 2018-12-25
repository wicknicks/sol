package io.sol.main.java.io.sol;

import org.apache.kafka.common.serialization.Serde;

public interface SerializationFactory {

    <T> Serde<T> getSerdesFor(Class<T> klass);

}
