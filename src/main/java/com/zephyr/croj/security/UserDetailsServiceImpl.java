package com.zephyr.croj.security;

import com.zephyr.croj.mapper.UserMapper;
import com.zephyr.croj.model.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * 用户详情服务实现
 * 负责从数据库加载用户信息
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserMapper userMapper;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userMapper.findByUsernameOrEmail(username);
        if (user == null) {
            log.error("用户不存在: {}", username);
            throw new UsernameNotFoundException("用户不存在: " + username);
        }

        // 检查用户状态
        if (user.getStatus() == 1) {
            log.error("用户已被禁用: {}", username);
            throw new UsernameNotFoundException("账号已被禁用");
        }

        // 转换用户角色为Spring Security的权限
        List<SimpleGrantedAuthority> authorities = Collections.singletonList(
                new SimpleGrantedAuthority("ROLE_" + getRoleName(user.getRole()))
        );

        return new org.springframework.security.core.userdetails.User(
                user.getUsername(),
                user.getPassword(),
                authorities);
    }

    /**
     * 获取角色名称
     */
    private String getRoleName(Integer roleCode) {
        return switch (roleCode) {
            case 0 -> "USER";
            case 1 -> "ADMIN";
            case 2 -> "SUPER_ADMIN";
            default -> "UNKNOWN";
        };
    }
}