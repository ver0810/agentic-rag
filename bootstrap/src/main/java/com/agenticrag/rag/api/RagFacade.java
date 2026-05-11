package com.agenticrag.rag.api;

import com.agenticrag.rag.query.RagQueryResult;

public interface RagFacade {

    RagQueryResult query(RagQueryRequest request);
}
