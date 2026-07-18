package com.zephyr.croj.security;

import com.zephyr.croj.config.properties.JudgeResultProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
public class JudgeServiceTokenFilter extends OncePerRequestFilter {
    public static final String TOKEN_HEADER = "X-CROJ-Service-Token";
    private final JudgeResultProperties properties;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String requestPath = request.getRequestURI();
        if (!request.getContextPath().isEmpty() && requestPath.startsWith(request.getContextPath())) {
            requestPath = requestPath.substring(request.getContextPath().length());
        }
        return !"POST".equals(request.getMethod())
                || !"/internal/v1/judge-results".equals(requestPath);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String supplied = request.getHeader(TOKEN_HEADER);
        if (supplied == null || !MessageDigest.isEqual(
                properties.serviceToken().getBytes(StandardCharsets.UTF_8),
                supplied.getBytes(StandardCharsets.UTF_8))) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "judge-service",
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_JUDGE_SERVICE"))));
        filterChain.doFilter(request, response);
    }
}
