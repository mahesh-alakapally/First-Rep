package com.firstrep.storage;

import java.time.Duration;

public interface ObjectStorage {
    void put(String key, byte[] body, String contentType);

    byte[] get(String key);

    void delete(String key);

    String presignPut(String key, String contentType, Duration ttl);
}
