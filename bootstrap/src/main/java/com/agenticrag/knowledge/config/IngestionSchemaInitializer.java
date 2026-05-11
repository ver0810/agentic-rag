package com.agenticrag.knowledge.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class IngestionSchemaInitializer {

    private final JdbcTemplate jdbcTemplate;

    public IngestionSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void ensureIngestionSchema() {
        try {
            addColumnIfMissing("t_ingestion_task", "retry_count", "INTEGER DEFAULT 0");
            addColumnIfMissing("t_ingestion_task", "max_retries", "INTEGER DEFAULT 3");
            addColumnIfMissing("t_ingestion_task", "next_run_at", "TIMESTAMP");
            addColumnIfMissing("t_ingestion_task", "lease_owner", "VARCHAR(64)");
            addColumnIfMissing("t_ingestion_task", "lease_until", "TIMESTAMP");
        } catch (Exception ex) {
            log.warn("Failed to initialize ingestion schema automatically: {}", ex.getMessage());
        }
    }

    private void addColumnIfMissing(String tableName, String columnName, String ddl) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = current_schema()
                  AND table_name = ?
                  AND column_name = ?
                """,
                Integer.class,
                tableName,
                columnName);
        if (count != null && count > 0) {
            return;
        }
        jdbcTemplate.execute("ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + ddl);
    }
}
