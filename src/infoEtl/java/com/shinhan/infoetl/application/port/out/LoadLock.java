package com.shinhan.infoetl.application.port.out;

public interface LoadLock extends AutoCloseable {

    @Override
    void close();
}
