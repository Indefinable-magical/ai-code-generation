package com.yu.aicodeGeneration.service;

import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import com.yu.aicodeGeneration.model.dto.app.AppAddRequest;
import com.yu.aicodeGeneration.model.dto.app.AppQueryRequest;
import com.yu.aicodeGeneration.model.entity.App;
import com.yu.aicodeGeneration.model.entity.User;
import com.yu.aicodeGeneration.model.vo.AppVO;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 应用 服务层。
 * 学习提示：AppService 是应用生成业务的主入口，Controller 调用它，内部再串起 AI 生成、保存、部署、截图等能力。
 */
public interface AppService extends IService<App> {

    /**
     * 通过对话生成应用代码
     *
     * @param appId     应用 ID
     * @param message   提示词
     * @param loginUser 登录用户
     * @return
     */
    Flux<String> chatToGenCode(Long appId, String message, User loginUser);

    /**
     * 停止应用代码生成
     *
     * @param appId     应用 ID
     * @param loginUser 登录用户
     * @return 是否停止成功
     */
    Boolean stopGenCode(Long appId, User loginUser);

    /**
     * 创建应用
     *
     * @param appAddRequest
     * @param loginUser
     * @return
     */
    Long createApp(AppAddRequest appAddRequest, User loginUser);

    /**
     * 应用部署
     *
     * @param appId     应用 ID
     * @param loginUser 登录用户
     * @return 可访问的部署地址
     */
    String deployApp(Long appId, User loginUser);

    /**
     * 异步生成应用截图并更新封面
     *
     * @param appId  应用ID
     * @param appUrl 应用访问URL
     */
    void generateAppScreenshotAsync(Long appId, String appUrl);

    /**
     * 获取应用封装类
     *
     * @param app
     * @return
     */
    AppVO getAppVO(App app);

    /**
     * 判断应用是否对当前用户可见
     *
     * @param app       应用
     * @param loginUser 当前登录用户，可为空
     * @return 是否可见
     */
    boolean isAppVisibleToUser(App app, User loginUser);

    /**
     * 校验并规范化更新场景下的可见范围
     *
     * @param visibility 应用可见范围
     * @return 合法可见范围；为空时返回 null，表示不更新该字段
     */
    String getValidVisibility(String visibility);

    /**
     * 格式化应用标签
     *
     * @param tagList 标签列表
     * @return 数据库存储用的标签字符串
     */
    String formatTags(List<String> tagList);

    /**
     * 获取应用封装类列表
     *
     * @param appList
     * @return
     */
    List<AppVO> getAppVOList(List<App> appList);

    /**
     * 构造应用查询条件
     *
     * @param appQueryRequest
     * @return
     */
    QueryWrapper getQueryWrapper(AppQueryRequest appQueryRequest);

}
