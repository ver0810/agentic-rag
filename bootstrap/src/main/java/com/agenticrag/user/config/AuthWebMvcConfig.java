package com.agenticrag.user.config;

import com.agenticrag.user.auth.CurrentUserIdArgumentResolver;
import com.agenticrag.user.auth.JwtAuthenticationInterceptor;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import com.agenticrag.user.auth.JwtProperties;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;

@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class AuthWebMvcConfig implements WebMvcConfigurer {

    private final JwtAuthenticationInterceptor jwtAuthenticationInterceptor;
    private final CurrentUserIdArgumentResolver currentUserIdArgumentResolver;
    private final ThreadPoolTaskExecutor applicationTaskExecutor;

    public AuthWebMvcConfig(JwtAuthenticationInterceptor jwtAuthenticationInterceptor,
                            CurrentUserIdArgumentResolver currentUserIdArgumentResolver,
                            ThreadPoolTaskExecutor applicationTaskExecutor) {
        this.jwtAuthenticationInterceptor = jwtAuthenticationInterceptor;
        this.currentUserIdArgumentResolver = currentUserIdArgumentResolver;
        this.applicationTaskExecutor = applicationTaskExecutor;
    }

    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        configurer.setTaskExecutor(applicationTaskExecutor);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtAuthenticationInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/user/login",
                        "/user/register",
                        "/user/refresh",
                        "/error"
                );
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentUserIdArgumentResolver);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
