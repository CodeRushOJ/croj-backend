package com.zephyr.croj.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.crypto.SecretKey;
import javax.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * JWT Token 提供者
 * 负责生成、验证和从请求中解析JWT Token
 */
@Component
@Slf4j
public class JwtTokenProvider {

    @Value("${jwt.secret}")
    private String secretKeyString;

    @Value("${jwt.expiration}")
    private long tokenValidityInMilliseconds;

    @Value("${jwt.header}")
    private String authorizationHeader;

    @Value("${jwt.tokenPrefix}")
    private String tokenPrefix;

    private SecretKey secretKey;

    @PostConstruct
    protected void init() {
        // 使用HMAC-SHA算法创建密钥
        secretKey = Keys.hmacShaKeyFor(secretKeyString.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 为用户创建JWT Token
     *
     * @param userId   用户ID
     * @param username 用户名
     * @param roles    用户角色
     * @return JWT Token
     */
    public String createToken(Long userId, String username, List<String> roles) {
        Date now = new Date();
        Date validity = new Date(now.getTime() + tokenValidityInMilliseconds);

        // 确保角色以"ROLE_"开头
        List<String> formattedRoles = roles.stream()
                .map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role)
                .toList();

        return Jwts.builder()
                .setSubject(username)
                .claim("userId", userId)
                .claim("roles", formattedRoles)
                .setIssuedAt(now)
                .setExpiration(validity)
                .signWith(secretKey)
                .compact();
    }

    /**
     * 从Token中获取认证信息
     *
     * @param token JWT Token
     * @return 认证信息
     */
    public Authentication getAuthentication(String token) {
        Claims claims = extractAllClaims(token);

        Long userId = claims.get("userId", Long.class);
        String username = claims.getSubject();
        Collection<? extends GrantedAuthority> authorities = getRolesFromClaims(claims);

        UserDetails userDetails = new User(username, "", authorities);
        return new UsernamePasswordAuthenticationToken(userDetails, "", authorities);
    }

    /**
     * 从Token的claims中获取用户角色
     *
     * @param claims Token claims
     * @return 用户角色
     */
    @SuppressWarnings("unchecked")
    private Collection<? extends GrantedAuthority> getRolesFromClaims(Claims claims) {
        List<String> roles = (List<String>) claims.get("roles");
        return roles.stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());
    }

    /**
     * 从请求中解析Token
     *
     * @param request HTTP请求
     * @return Token
     */
    public String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader(authorizationHeader);
        if (bearerToken != null && bearerToken.startsWith(tokenPrefix + " ")) {
            return bearerToken.substring(tokenPrefix.length() + 1);
        }
        return null;
    }

    /**
     * 验证Token是否有效
     *
     * @param token JWT Token
     * @return Token是否有效
     */
    public boolean validateToken(String token) {
        try {
            Claims claims = extractAllClaims(token);
            return !claims.getExpiration().before(new Date());
        } catch (Exception e) {
            log.error("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 从Token中获取所有claims
     *
     * @param token JWT Token
     * @return Claims
     */
    private Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(secretKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    /**
     * 从Token中获取用户ID
     *
     * @param token JWT Token
     * @return 用户ID
     */
    public Long getUserIdFromToken(String token) {
        Claims claims = extractAllClaims(token);
        return claims.get("userId", Long.class);
    }

    /**
     * 从Token中获取用户名
     *
     * @param token JWT Token
     * @return 用户名
     */
    public String getUsernameFromToken(String token) {
        Claims claims = extractAllClaims(token);
        return claims.getSubject();
    }
}