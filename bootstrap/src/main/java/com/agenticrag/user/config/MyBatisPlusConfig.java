package com.agenticrag.user.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan({
        "com.agenticrag.feedback.dao.mapper",
        "com.agenticrag.knowledge.dao.mapper",
        "com.agenticrag.rageval.dao.mapper",
        "com.agenticrag.ragtrace.dao.mapper",
        "com.agenticrag.user.dao.mapper",
})
public class MyBatisPlusConfig {
}
