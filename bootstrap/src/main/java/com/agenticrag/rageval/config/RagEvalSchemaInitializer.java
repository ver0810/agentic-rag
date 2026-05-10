package com.agenticrag.rageval.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class RagEvalSchemaInitializer {

    private final JdbcTemplate jdbcTemplate;

    public RagEvalSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void ensureSchema() {
        try {
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS t_rag_eval_run
                    (
                        id                VARCHAR(20) NOT NULL PRIMARY KEY,
                        run_id            VARCHAR(64) NOT NULL,
                        dataset_name      VARCHAR(128) NOT NULL,
                        kb_id             VARCHAR(20),
                        user_id           VARCHAR(20) NOT NULL,
                        top_k             INTEGER,
                        total_count       INTEGER     NOT NULL DEFAULT 0,
                        passed_count      INTEGER     NOT NULL DEFAULT 0,
                        failed_count      INTEGER     NOT NULL DEFAULT 0,
                        pass_rate         DOUBLE PRECISION,
                        answer_accuracy   DOUBLE PRECISION,
                        citation_hit_rate DOUBLE PRECISION,
                        refusal_accuracy  DOUBLE PRECISION,
                        executed_at       TIMESTAMP   NOT NULL,
                        create_time       TIMESTAMP            DEFAULT CURRENT_TIMESTAMP,
                        update_time       TIMESTAMP            DEFAULT CURRENT_TIMESTAMP,
                        deleted           SMALLINT             DEFAULT 0,
                        CONSTRAINT uk_rag_eval_run_id UNIQUE (run_id)
                    )
                    """);
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_rag_eval_run_user ON t_rag_eval_run (user_id, executed_at)");
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_rag_eval_run_dataset ON t_rag_eval_run (dataset_name, executed_at)");

            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS t_rag_eval_case_result
                    (
                        id                         VARCHAR(20)  NOT NULL PRIMARY KEY,
                        eval_run_id                VARCHAR(64)  NOT NULL,
                        case_id                    VARCHAR(128) NOT NULL,
                        kb_id                      VARCHAR(20),
                        query_text                 TEXT         NOT NULL,
                        trace_id                   VARCHAR(64),
                        rewritten_query            TEXT,
                        passed                     SMALLINT     NOT NULL DEFAULT 0,
                        answer_passed              SMALLINT     NOT NULL DEFAULT 0,
                        citation_passed            SMALLINT     NOT NULL DEFAULT 0,
                        refusal_passed             SMALLINT     NOT NULL DEFAULT 0,
                        expected_answer_term_count INTEGER      NOT NULL DEFAULT 0,
                        matched_answer_term_count  INTEGER      NOT NULL DEFAULT 0,
                        expected_doc_names         TEXT,
                        matched_doc_names          TEXT,
                        answer_text                TEXT,
                        failure_reason             VARCHAR(255),
                        create_time                TIMESTAMP             DEFAULT CURRENT_TIMESTAMP,
                        update_time                TIMESTAMP             DEFAULT CURRENT_TIMESTAMP,
                        deleted                    SMALLINT              DEFAULT 0
                    )
                    """);
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_rag_eval_case_run ON t_rag_eval_case_result (eval_run_id, case_id)");
        } catch (Exception ex) {
            log.warn("Failed to initialize RAG eval schema automatically: {}", ex.getMessage());
        }
    }
}
