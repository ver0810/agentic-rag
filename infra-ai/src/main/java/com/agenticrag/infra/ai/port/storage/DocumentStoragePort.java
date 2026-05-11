package com.agenticrag.infra.ai.port.storage;

import java.io.InputStream;

public interface DocumentStoragePort {

    String store(InputStream inputStream, String path, String contentType);

    InputStream load(String path);

    void delete(String path);

    String getUrl(String path);
}
