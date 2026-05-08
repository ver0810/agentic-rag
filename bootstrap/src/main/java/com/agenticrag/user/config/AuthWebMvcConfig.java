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

@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class AuthWebMvcConfig implements WebMvcConfigurer {

    private final JwtAuthenticationInterceptor jwtAuthenticationInterceptor;
    private final CurrentUserIdArgumentResolver currentUserIdArgumentResolver;

    public AuthWebMvcConfig(JwtAuthenticationInterceptor jwtAuthenticationInterceptor,
                            CurrentUserIdArgumentResolver currentUserIdArgumentResolver) {
        this.jwtAuthenticationInterceptor = jwtAuthenticationInterceptor;
        this.currentUserIdArgumentResolver = currentUserIdArgumentResolver;
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
