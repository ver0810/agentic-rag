package com.agenticrag.user.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan({"com.agenticrag.user.dao.mapper", "com.agenticrag.knowledge.dao.mapper"})
public class MyBatisPlusConfig {
}
