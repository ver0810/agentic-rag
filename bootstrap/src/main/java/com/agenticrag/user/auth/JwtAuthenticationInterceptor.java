package com.agenticrag.user.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class JwtAuthenticationInterceptor implements HandlerInterceptor {

    private final JwtTokenService jwtTokenService;
    private final TokenBlacklistService tokenBlacklistService;
    private final ObjectMapper objectMapper;

    public JwtAuthenticationInterceptor(JwtTokenService jwtTokenService,
                                        TokenBlacklistService tokenBlacklistService,
                                        ObjectMapper objectMapper) {
        this.jwtTokenService = jwtTokenService;
        this.tokenBlacklistService = tokenBlacklistService;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(authorization) || !authorization.startsWith(AuthConstants.TOKEN_PREFIX)) {
            writeUnauthorized(response, "未登录或Token缺失");
            return false;
        }
        String token = authorization.substring(AuthConstants.TOKEN_PREFIX.length()).trim();
        if (!StringUtils.hasText(token)) {
            writeUnauthorized(response, "Token不能为空");
            return false;
        }
        if (tokenBlacklistService.isBlacklisted(token)) {
            writeUnauthorized(response, "Token已失效，请重新登录");
            return false;
        }
        try {
            AuthenticatedUser currentUser = jwtTokenService.parseAccessToken(token);
            request.setAttribute(AuthConstants.CURRENT_USER, currentUser);
            if (handler instanceof HandlerMethod handlerMethod) {
                RequireRole requireRole = findRequireRole(handlerMethod);
                if (requireRole != null && !requireRole.value().equals(currentUser.getRole())) {
                    writeForbidden(response, "无权访问该资源");
                    return false;
                }
            }
            return true;
        } catch (JwtException | IllegalArgumentException ex) {
            writeUnauthorized(response, "Token无效或已过期");
            return false;
        }
    }

    private RequireRole findRequireRole(HandlerMethod handlerMethod) {
        RequireRole methodRole = AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getMethod(), RequireRole.class);
        if (!ObjectUtils.isEmpty(methodRole)) {
            return methodRole;
        }
        return AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getBeanType(), RequireRole.class);
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws Exception {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), Map.of("message", message));
    }

    private void writeForbidden(HttpServletResponse response, String message) throws Exception {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), Map.of("message", message));
    }
}
