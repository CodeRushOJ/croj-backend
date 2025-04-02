package com.zephyr.croj.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zephyr.croj.model.entity.User;
import com.zephyr.croj.service.AdminService;
import com.zephyr.croj.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 管理员服务实现类
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminServiceImpl implements AdminService {

    private final UserMapper userMapper;

    @Override
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();

        try {
            // 计算用户统计数据
            long totalUsers = userMapper.selectCount(
                    new LambdaQueryWrapper<User>()
                            .eq(User::getIsDeleted, 0)
            );

            // 计算活跃用户数（状态为正常的用户）
            long activeUsers = userMapper.selectCount(
                    new LambdaQueryWrapper<User>()
                            .eq(User::getIsDeleted, 0)
                            .eq(User::getStatus, 0)
            );

            // 计算已验证邮箱的用户数
            long verifiedUsers = userMapper.selectCount(
                    new LambdaQueryWrapper<User>()
                            .eq(User::getIsDeleted, 0)
                            .eq(User::getEmailVerified, 1)
            );

            // 计算今日注册用户数
            LocalDateTime todayStart = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
            LocalDateTime todayEnd = LocalDateTime.of(LocalDate.now(), LocalTime.MAX);

            long todayUsers = userMapper.selectCount(
                    new LambdaQueryWrapper<User>()
                            .eq(User::getIsDeleted, 0)
                            .between(User::getCreateTime, todayStart, todayEnd)
            );

            // 添加到结果Map
            stats.put("userCount", totalUsers);
            stats.put("activeUserCount", activeUsers);
            stats.put("verifiedUserCount", verifiedUsers);
            stats.put("todayUserCount", todayUsers);

            // 未来可以添加问题和提交统计
            stats.put("problemCount", 0);
            stats.put("submissionCount", 0);
            stats.put("contestCount", 0);

        } catch (Exception e) {
            log.error("获取统计数据时出错", e);
            // 发生错误时返回空结果
            stats.put("userCount", 0);
            stats.put("activeUserCount", 0);
            stats.put("verifiedUserCount", 0);
            stats.put("todayUserCount", 0);
        }

        return stats;
    }
}