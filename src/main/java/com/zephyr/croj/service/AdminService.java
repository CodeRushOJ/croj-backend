package com.zephyr.croj.service;

import java.util.Map;

/**
 * 管理员服务接口
 */
public interface AdminService {

    /**
     * 获取管理统计数据
     *
     * @return 统计数据映射
     */
    Map<String, Object> getStatistics();
}