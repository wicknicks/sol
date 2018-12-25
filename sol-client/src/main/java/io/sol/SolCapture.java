package io.sol;

import java.io.Closeable;

public interface SolCapture extends Closeable {

    void put(String key, Object val);

    void close();

}
