package com.agenticrag.infra.ai.rag.vector;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class VectorSchemaInitializer {

    private final JdbcTemplate jdbcTemplate;

    public VectorSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void alignVectorSchema() {
        try {
            String currentType = jdbcTemplate.query(
                    """
                    SELECT format_type(a.atttypid, a.atttypmod)
                    FROM pg_attribute a
                    JOIN pg_class c ON a.attrelid = c.oid
                    JOIN pg_namespace n ON c.relnamespace = n.oid
                    WHERE n.nspname = current_schema()
                      AND c.relname = 't_knowledge_vector'
                      AND a.attname = 'embedding'
                      AND a.attnum > 0
                      AND NOT a.attisdropped
                    """,
                    rs -> rs.next() ? rs.getString(1) : null);

            if (currentType == null) {
                log.warn("Vector schema initializer skipped because t_knowledge_vector.embedding was not found");
                return;
            }

            if ("vector".equalsIgnoreCase(currentType)) {
                return;
            }

            log.info("Altering t_knowledge_vector.embedding from {} to vector to support provider-specific dimensions", currentType);
            jdbcTemplate.execute("DROP INDEX IF EXISTS idx_kv_embedding");
            jdbcTemplate.execute("ALTER TABLE t_knowledge_vector ALTER COLUMN embedding TYPE vector");
        } catch (Exception e) {
            log.warn("Failed to align vector schema automatically: {}", e.getMessage());
        }
    }
}
