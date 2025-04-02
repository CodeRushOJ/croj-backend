package com.zephyr.croj.config;

import com.zephyr.croj.security.JwtAccessDeniedHandler;
import com.zephyr.croj.security.JwtAuthenticationEntryPoint;
import com.zephyr.croj.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Spring Security 配置类
 */
@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final JwtAccessDeniedHandler jwtAccessDeniedHandler;

    /**
     * 密码编码器
     * BCrypt 是一种安全的密码哈希算法，会自动处理盐值
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 认证管理器
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration)
            throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    /**
     * CORS configuration
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("http://localhost:3000"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With"));
        configuration.setExposedHeaders(List.of("Authorization", "Captcha-Key")); // 添加 Captcha-Key
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    /**
     * 安全过滤器链配置
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // Enable CORS and disable CSRF
                .cors().configurationSource(corsConfigurationSource())
                .and()
                .csrf().disable()
                // 异常处理
                .exceptionHandling()
                .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                .accessDeniedHandler(jwtAccessDeniedHandler)
                .and()
                // 使用无状态会话，不需要session
                .sessionManagement()
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                .and()
                // 请求授权规则
                .authorizeRequests()
                // 允许所有人访问公共资源
                .antMatchers(
                        "/swagger-ui.html",
                        "/swagger-ui/**",
                        "/swagger-ui/index.html",
                        "/swagger-ui/swagger-ui.css",
                        "/swagger-ui/swagger-ui-bundle.js",
                        "/swagger-ui/swagger-ui-standalone-preset.js",
                        "/v3/api-docs/**",
                        "/v3/api-docs.yaml",
                        "/swagger-resources/**",
                        "/webjars/**"
                ).permitAll()
                // 允许所有人访问上传的文件资源
                .antMatchers("/uploads/**").permitAll()
                // 允许所有人访问登录和注册接口
                .antMatchers(
                        "/user/register",
                        "/user/login",
                        "/user/check/username/**",
                        "/user/check/email/**",
                        "/captcha/**",
                        "/email/code"
                ).permitAll()
                // OPTIONS 请求放行
                .antMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                // 管理员接口权限
                .antMatchers("/user/list").hasAnyRole("ADMIN", "SUPER_ADMIN")
                .antMatchers("/user/status/**").hasAnyRole("ADMIN", "SUPER_ADMIN")
                .antMatchers("/admin/**").hasAnyRole("ADMIN", "SUPER_ADMIN")
                .antMatchers(HttpMethod.DELETE, "/user/**").hasAnyRole("ADMIN", "SUPER_ADMIN")
                // 其他所有请求需要认证
                .anyRequest().authenticated();

        // 添加JWT过滤器
        http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}