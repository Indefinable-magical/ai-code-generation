package com.yu.aicodeGeneration.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.yu.aicodeGeneration.exception.BusinessException;
import com.yu.aicodeGeneration.exception.ErrorCode;
import com.yu.aicodeGeneration.model.dto.user.UserQueryRequest;
import com.yu.aicodeGeneration.model.entity.User;
import com.yu.aicodeGeneration.mapper.UserMapper;
import com.yu.aicodeGeneration.model.enums.UserRoleEnum;
import com.yu.aicodeGeneration.model.vo.LoginUserVO;
import com.yu.aicodeGeneration.model.vo.UserVO;
import com.yu.aicodeGeneration.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static com.yu.aicodeGeneration.constant.UserConstant.USER_LOGIN_STATE;

/**
 * 用户 服务层实现。
 *
 * 学习重点：
 * 这里负责用户注册、登录、登录态获取、用户脱敏和用户查询条件构造。
 * 登录态保存在 session 中，前端请求通过 Cookie 携带 sessionId。
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    @Override
    public long userRegister(String userAccount, String userPassword, String checkPassword) {
        // 1. 校验参数：注册入口必须先拦截空值和明显不合法的账号密码。
        if (StrUtil.hasBlank(userAccount, userPassword, checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }
        if (userAccount.length() < 4) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号长度过短");
        }
        if (userPassword.length() < 6 || checkPassword.length() < 6) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "密码长度过短");
        }
        if (!userPassword.equals(checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "两次输入的密码不一致");
        }
        // 2. 查询用户是否已存在，userAccount 有唯一性要求。
        QueryWrapper queryWrapper = new QueryWrapper();
        queryWrapper.eq("userAccount", userAccount);
        long count = this.mapper.selectCountByQuery(queryWrapper);
        if (count > 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号重复");
        }
        // 3. 加密密码：数据库不保存明文密码。
        String encryptPassword = getEncryptPassword(userPassword);
        // 4. 创建用户，插入数据库。
        User user = new User();
        user.setUserAccount(userAccount);
        user.setUserPassword(encryptPassword);
        // 默认昵称和默认角色，后续可通过用户更新接口修改展示信息。
        user.setUserName("无名");
        user.setUserRole(UserRoleEnum.USER.getValue());
        boolean saveResult = this.save(user);
        if (!saveResult) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "注册失败，数据库错误");
        }
        return user.getId();
    }

    @Override
    public LoginUserVO getLoginUserVO(User user) {
        // 返回给前端的登录用户信息必须脱敏，不能包含 userPassword 等敏感字段。
        if (user == null) {
            return null;
        }
        LoginUserVO loginUserVO = new LoginUserVO();
        BeanUtil.copyProperties(user, loginUserVO);
        return loginUserVO;
    }

    @Override
    public LoginUserVO userLogin(String userAccount, String userPassword, HttpServletRequest request) {
        // 1. 校验参数：登录也要限制账号密码基本格式。
        if (StrUtil.hasBlank(userAccount, userPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }
        if (userAccount.length() < 4) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号长度过短");
        }
        if (userPassword.length() < 6) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "密码长度过短");
        }
        // 2. 加密：用同样的加密逻辑把输入密码转换成数据库中的存储形式。
        String encryptPassword = getEncryptPassword(userPassword);
        // 3. 查询用户是否存在。账号和加密密码都匹配才算登录成功。
        QueryWrapper queryWrapper = new QueryWrapper();
        queryWrapper.eq("userAccount", userAccount);
        queryWrapper.eq("userPassword", encryptPassword);
        User user = this.mapper.selectOneByQuery(queryWrapper);
        if (user == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户不存在或密码错误");
        }
        // 4. 如果用户存在，记录用户的登录态到 session。
        // 后续请求会通过 Cookie 中的 sessionId 找回这个对象。
        request.getSession().setAttribute(USER_LOGIN_STATE, user);
        // 5. 返回脱敏的用户信息给前端 Pinia store。
        return this.getLoginUserVO(user);
    }

    @Override
    public User getLoginUser(HttpServletRequest request) {
        // 先从 session 判断用户是否登录。
        Object userObj = request.getSession().getAttribute(USER_LOGIN_STATE);
        User currentUser = (User) userObj;
        if (currentUser == null || currentUser.getId() == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        // 从数据库重新查询当前用户信息。
        // 这样可以拿到最新角色/昵称，也能避免 session 中用户信息过期。
        long userId = currentUser.getId();
        currentUser = this.getById(userId);
        if (currentUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        return currentUser;
    }

    @Override
    public User getLoginUserSilently(HttpServletRequest request) {
        try {
            return getLoginUser(request);
        } catch (BusinessException e) {
            // 只有未登录可以被吞掉，其它业务异常仍要抛出，避免隐藏真实错误。
            if (e.getCode() == ErrorCode.NOT_LOGIN_ERROR.getCode()) {
                return null;
            }
            throw e;
        }
    }

    @Override
    public UserVO getUserVO(User user) {
        // UserVO 是普通用户展示对象，同样不能包含密码等敏感信息。
        if (user == null) {
            return null;
        }
        UserVO userVO = new UserVO();
        BeanUtil.copyProperties(user, userVO);
        return userVO;
    }

    @Override
    public List<UserVO> getUserVOList(List<User> userList) {
        // 空列表直接返回空集合，避免调用方判空。
        if (CollUtil.isEmpty(userList)) {
            return new ArrayList<>();
        }
        // 批量把实体转换成 VO。
        return userList.stream()
                .map(this::getUserVO)
                .collect(Collectors.toList());
    }

    @Override
    public boolean userLogout(HttpServletRequest request) {
        // 先判断用户是否登录，未登录时退出没有意义。
        Object userObj = request.getSession().getAttribute(USER_LOGIN_STATE);
        if (userObj == null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "用户未登录");
        }
        // 移除 session 中的登录态，后续请求会被视为未登录。
        request.getSession().removeAttribute(USER_LOGIN_STATE);
        return true;
    }

    @Override
    public QueryWrapper getQueryWrapper(UserQueryRequest userQueryRequest) {
        // 后台用户列表查询的条件对象不能为空。
        if (userQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        // 拆出查询字段，后面统一构建 QueryWrapper。
        Long id = userQueryRequest.getId();
        String userAccount = userQueryRequest.getUserAccount();
        String userName = userQueryRequest.getUserName();
        String userProfile = userQueryRequest.getUserProfile();
        String userRole = userQueryRequest.getUserRole();
        String sortField = userQueryRequest.getSortField();
        String sortOrder = userQueryRequest.getSortOrder();
        // eq 是精确匹配，like 是模糊匹配；MyBatis-Flex 会忽略空值条件。
        return QueryWrapper.create()
                .eq("id", id) // where id = ${id}
                .eq("userRole", userRole) // and userRole = ${userRole}
                .like("userAccount", userAccount)
                .like("userName", userName)
                .like("userProfile", userProfile)
                .orderBy(sortField, "ascend".equals(sortOrder));
    }

    @Override
    public String getEncryptPassword(String userPassword) {
        // 盐值，混淆密码。
        // 学习提醒：MD5 + 固定盐只适合课程/练习项目，生产系统应使用 BCrypt/Argon2 等慢哈希。
        final String SALT = "yu";
        return DigestUtils.md5DigestAsHex((userPassword + SALT).getBytes(StandardCharsets.UTF_8));
    }
}
