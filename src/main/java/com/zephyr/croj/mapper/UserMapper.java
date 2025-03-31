package com.zephyr.croj.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zephyr.croj.model.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 用户Mapper接口
 *
 
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {

    /**
     * 根据用户名或邮箱查询用户
     *
     * @param account 用户名或邮箱
     * @return 用户实体
     */
    User findByUsernameOrEmail(@Param("account") String account);

    /**
     * 更新用户最后登录时间和IP
     *
     * @param userId 用户ID
     * @param ip 登录IP
     * @return 影响行数
     */
    @Update("UPDATE t_user SET last_login_time = NOW(), last_login_ip = #{ip} WHERE id = #{userId}")
    int updateLastLogin(@Param("userId") Long userId, @Param("ip") String ip);

    /**
     * 检查用户名是否存在（未删除的用户）
     *
     * @param username 用户名
     * @return 存在返回true，不存在返回false
     */
    boolean existsByUsername(@Param("username") String username);

    /**
     * 检查邮箱是否存在（未删除的用户）
     *
     * @param email 邮箱
     * @return 存在返回true，不存在返回false
     */
    boolean existsByEmail(@Param("email") String email);
}