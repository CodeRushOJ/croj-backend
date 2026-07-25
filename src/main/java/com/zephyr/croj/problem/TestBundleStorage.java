package com.zephyr.croj.problem;

public interface TestBundleStorage {
    void put(String objectKey, byte[] archive, String sha256);
}

