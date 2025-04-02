package com.zephyr.croj.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.zephyr.croj.model.dto.UserLoginDTO;
import com.zephyr.croj.model.dto.UserRegisterDTO;
import com.zephyr.croj.model.dto.UserUpdateDTO;
import com.zephyr.croj.model.entity.User;
import com.zephyr.croj.model.vo.UserVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * 用户服务接口
 */
public interface UserService extends IService<User> {

    /**
     * 用户注册
     *
     * @param registerDTO 注册信息
     * @return 用户ID
     */
    Long register(UserRegisterDTO registerDTO);

    /**
     * 用户登录
     *
     * @param loginDTO 登录信息
     * @param ip 登录IP
     * @return 登录成功返回token，失败返回null
     */
    String login(UserLoginDTO loginDTO, String ip);

    /**
     * 根据token获取当前登录用户信息
     *
     * @return 用户视图对象
     */
    UserVO getCurrentUser();

    /**
     * 更新用户信息
     *
     * @param userId 用户ID
     * @param updateDTO 更新信息
     * @return 是否更新成功
     */
    boolean updateUserInfo(Long userId, UserUpdateDTO updateDTO);

    /**
     * 修改密码
     *
     * @param userId 用户ID
     * @param oldPassword 旧密码
     * @param newPassword 新密码
     * @param confirmPassword 确认密码
     * @return 是否修改成功
     */
    boolean updatePassword(Long userId, String oldPassword, String newPassword, String confirmPassword);

    /**
     * 通过ID获取用户信息
     *
     * @param userId 用户ID
     * @return 用户视图对象
     */
    UserVO getUserById(Long userId);

    /**
     * 分页查询用户列表
     *
     * @param current 当前页
     * @param size 每页大小
     * @param keyword 关键字（用户名或邮箱）
     * @return 用户分页列表
     */
    Page<UserVO> listUsers(long current, long size, String keyword);

    /**
     * 禁用或启用用户
     *
     * @param userId 用户ID
     * @param status 状态：0-正常，1-禁用
     * @return 是否操作成功
     */
    boolean updateUserStatus(Long userId, Integer status);

    /**
     * 逻辑删除用户
     *
     * @param userId 用户ID
     * @return 是否删除成功
     */
    boolean removeUser(Long userId);

    /**
     * 检查用户名是否已存在
     *
     * @param username 用户名
     * @return 存在返回true，不存在返回false
     */
    boolean checkUsernameExists(String username);

    /**
     * 检查邮箱是否已存在
     *
     * @param email 邮箱
     * @return 存在返回true，不存在返回false
     */
    boolean checkEmailExists(String email);

    /**
     * 验证用户邮箱
     *
     * @param userId 用户ID
     * @return 是否验证成功
     */
    boolean verifyUserEmail(Long userId);

    /**
     * 发送邮箱验证码（用于注册）
     *
     * @param email 邮箱
     * @param username 用户名
     * @return 是否发送成功
     */
    boolean sendEmailVerificationCode(String email, String username);

    /**
     * 发送邮箱验证链接
     *
     * @param userId 用户ID
     * @return 是否发送成功
     */
    boolean sendVerificationLink(Long userId);

    /**
     * 用户实体转视图对象
     *
     * @param user 用户实体
     * @return 用户视图对象
     */
    UserVO convertToVO(User user);

    /**
     * Update user avatar
     *
     * @param userId    User ID
     * @param avatarUrl URL of the avatar
     * @return true if successful, false otherwise
     */
    boolean updateUserAvatar(Long userId, String avatarUrl);
}