package com.yu.aicodeGeneration.controller;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.yu.aicodeGeneration.annotation.AuthCheck;
import com.yu.aicodeGeneration.common.BaseResponse;
import com.yu.aicodeGeneration.common.DeleteRequest;
import com.yu.aicodeGeneration.common.ResultUtils;
import com.yu.aicodeGeneration.constant.AppConstant;
import com.yu.aicodeGeneration.constant.UserConstant;
import com.yu.aicodeGeneration.exception.BusinessException;
import com.yu.aicodeGeneration.exception.ErrorCode;
import com.yu.aicodeGeneration.exception.ThrowUtils;
import com.yu.aicodeGeneration.model.dto.app.*;
import com.yu.aicodeGeneration.model.entity.User;
import com.yu.aicodeGeneration.model.enums.AppVisibilityEnum;
import com.yu.aicodeGeneration.model.vo.AppVO;
import com.yu.aicodeGeneration.ratelimter.annotation.RateLimit;
import com.yu.aicodeGeneration.ratelimter.enums.RateLimitType;
import com.yu.aicodeGeneration.service.ProjectDownloadService;
import com.yu.aicodeGeneration.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import com.yu.aicodeGeneration.model.entity.App;
import com.yu.aicodeGeneration.service.AppService;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.File;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 应用控制层。
 *
 * 学习重点：
 * 1. Controller 是前端请求进入后端的第一站，只负责参数接收、基础校验和结果包装。
 * 2. 真正的业务逻辑会下沉到 AppService，避免 Controller 越写越臃肿。
 * 3. 本类最核心的接口是 /chat/gen/code，它使用 SSE 把 AI 生成过程一段段推给前端。
 */
@RestController
@RequestMapping("/app")
public class AppController {

    // 应用业务服务：负责创建应用、生成代码、部署、查询列表等应用相关业务。
    @Resource
    private AppService appService;

    // 用户服务：这里主要用于从 session/request 中拿到当前登录用户。
    @Resource
    private UserService userService;

    // 项目下载服务：负责把生成后的项目目录打包成 zip 并写入 HTTP 响应。
    @Resource
    private ProjectDownloadService projectDownloadService;

    /*
     * SSE 代码生成接口。
     *
     * 调用链：
     * 前端 EventSource -> AppController.chatToGenCode
     * -> AppServiceImpl.chatToGenCode
     * -> AiCodeGeneratorFacade.generateAndSaveCodeStream
     * -> LangChain4j 流式模型
     *
     * produces = text/event-stream 表示这是一个服务端事件流接口，
     * 浏览器不会等全部响应结束，而是收到一段就处理一段。
     */
    @GetMapping(value = "/chat/gen/code", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    // 对当前接口按用户维度进行限流：每个用户 60 秒内最多请求 5 次，超出后返回指定提示信息
    @RateLimit(limitType = RateLimitType.USER, rate = 5, rateInterval = 60, message = "AI 对话请求过于频繁，请稍后再试")
    public Flux<ServerSentEvent<String>> chatToGenCode(@RequestParam Long appId,
                                                       @RequestParam String message,
                                                       HttpServletRequest request) {
        // 参数校验：越靠近入口越早拦截错误，后面的 service 就可以少处理无意义请求。
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 id 错误");
        ThrowUtils.throwIf(StrUtil.isBlank(message), ErrorCode.PARAMS_ERROR, "提示词不能为空");
        // 获取当前登录用户：后续业务需要用它做权限校验，确保用户只能操作自己的应用。
        User loginUser = userService.getLoginUser(request);
        // 调用服务生成代码。这里拿到的 Flux<String> 表示后端会持续吐出多个字符串片段。
        Flux<String> contentFlux = appService.chatToGenCode(appId, message, loginUser);
        return contentFlux
                .map(chunk -> {
                    // 前端 EventSource 的 onmessage 里按 JSON 读取 data，所以这里把每个片段包成 {"d": "..."}。
                    // 包一层 d 的好处是：未来如果要加 messageId、type 等字段，不需要改变 SSE 协议形态。
                    Map<String, String> wrapper = Map.of("d", chunk);
                    String jsonData = JSONUtil.toJsonStr(wrapper);
                    // ServerSentEvent 是 Spring 对 SSE 数据帧的封装；不指定 event 时，前端走 onmessage。
                    return ServerSentEvent.<String>builder()
                            .data(jsonData)
                            .build();
                })
                .concatWith(Mono.just(
                        // 发送结束事件：前端监听 done 后关闭 EventSource，并刷新预览地址。
                        ServerSentEvent.<String>builder()
                                .event("done")
                                .data("")
                                .build()
                ));
    }

    /**
     * 停止 AI 代码生成
     *
     * @param appStopGenerationRequest 停止生成请求
     * @param request                  请求
     * @return 是否停止成功
     */
    @PostMapping("/chat/gen/code/stop")
    public BaseResponse<Boolean> stopGenCode(@RequestBody AppStopGenerationRequest appStopGenerationRequest,
                                             HttpServletRequest request) {
        // 停止生成是用户主动操作，必须明确传入 appId，避免误停其它应用的生成流。
        ThrowUtils.throwIf(appStopGenerationRequest == null, ErrorCode.PARAMS_ERROR);
        Long appId = appStopGenerationRequest.getAppId();
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 错误");
        // 只有应用创建者或管理员可以停止生成，权限校验统一放到 service 中兜底。
        User loginUser = userService.getLoginUser(request);
        Boolean result = appService.stopGenCode(appId, loginUser);
        return ResultUtils.success(result);
    }

    /**
     * 应用部署
     *
     * @param appDeployRequest 部署请求
     * @param request          请求
     * @return 部署 URL
     */
    @PostMapping("/deploy")
    public BaseResponse<String> deployApp(@RequestBody AppDeployRequest appDeployRequest, HttpServletRequest request) {
        // 请求体为空时，说明前端没有传 appId，直接返回参数错误。
        ThrowUtils.throwIf(appDeployRequest == null, ErrorCode.PARAMS_ERROR);
        // 部署的最小单位是应用，所以部署请求里必须有 appId。
        Long appId = appDeployRequest.getAppId();
        // appId 要是正数；0、负数、null 都不是有效数据库主键。
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 不能为空");
        // 部署属于用户敏感操作，必须知道当前用户是谁。
        User loginUser = userService.getLoginUser(request);
        // 具体部署逻辑交给 service：包括权限判断、目录复制、Vue 构建、数据库更新等。
        String deployUrl = appService.deployApp(appId, loginUser);
        // Controller 统一用 BaseResponse 包装返回值，前端只需要判断 code 是否为 0。
        return ResultUtils.success(deployUrl);
    }

    /**
     * 下载应用代码
     *
     * @param appId    应用ID
     * @param request  请求
     * @param response 响应
     */
    @GetMapping("/download/{appId}")
    public void downloadAppCode(@PathVariable Long appId,
                                HttpServletRequest request,
                                HttpServletResponse response) {
        // 1. 基础校验：路径参数来自 URL，同样不能信任。
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用ID无效");
        // 2. 查询应用信息：后面要用 app 的 userId 和 codeGenType。
        App app = appService.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        // 3. 权限校验：只有应用创建者可以下载代码，避免用户拿到别人的源码。
        User loginUser = userService.getLoginUser(request);
        if (!app.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限下载该应用代码");
        }
        // 4. 构建应用代码目录路径（生成目录，非部署目录）。
        // 目录命名规则必须和 CodeFileSaverTemplate 中的保存规则保持一致：类型_appId。
        String codeGenType = app.getCodeGenType();
        String sourceDirName = codeGenType + "_" + appId;
        String sourceDirPath = AppConstant.CODE_OUTPUT_ROOT_DIR + File.separator + sourceDirName;
        // 5. 检查代码目录是否存在：如果用户还没生成过代码，就无法下载。
        File sourceDir = new File(sourceDirPath);
        ThrowUtils.throwIf(!sourceDir.exists() || !sourceDir.isDirectory(),
                ErrorCode.NOT_FOUND_ERROR, "应用代码不存在，请先生成代码");
        // 6. 生成下载文件名（不建议添加中文内容，减少浏览器兼容问题）。
        String downloadFileName = String.valueOf(appId);
        // 7. 调用通用下载服务：它会负责 zip 压缩、响应头、输出流写入。
        projectDownloadService.downloadProjectAsZip(sourceDirPath, downloadFileName, response);
    }

    /**
     * 创建应用
     *
     * @param appAddRequest 创建应用请求
     * @param request       请求
     * @return 应用 id
     */
    @PostMapping("/add")
    public BaseResponse<Long> addApp(@RequestBody AppAddRequest appAddRequest, HttpServletRequest request) {
        // 创建应用的核心输入是 initPrompt；这里先判断请求体本身是否存在。
        ThrowUtils.throwIf(appAddRequest == null, ErrorCode.PARAMS_ERROR);
        // 获取当前登录用户：新建应用要绑定到用户 ID，后续权限都依赖这个关系。
        User loginUser = userService.getLoginUser(request);
        // createApp 内部会保存应用，并调用 AI 路由模型决定生成类型。
        Long appId = appService.createApp(appAddRequest, loginUser);
        return ResultUtils.success(appId);
    }

    /**
     * 更新应用（用户只能更新自己的应用名称）
     *
     * @param appUpdateRequest 更新请求
     * @param request          请求
     * @return 更新结果
     */
    @PostMapping("/update")
    public BaseResponse<Boolean> updateApp(@RequestBody AppUpdateRequest appUpdateRequest, HttpServletRequest request) {
        // 更新必须带 id，否则不知道要改哪条应用记录。
        if (appUpdateRequest == null || appUpdateRequest.getId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 用户只能更新自己的应用，因此需要拿当前登录用户做 owner 校验。
        User loginUser = userService.getLoginUser(request);
        long id = appUpdateRequest.getId();
        // 判断是否存在：不存在时返回 NOT_FOUND，比直接 update 失败更清晰。
        App oldApp = appService.getById(id);
        ThrowUtils.throwIf(oldApp == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人可更新：普通用户不能改别人的应用名。
        if (!oldApp.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        // 这里只允许用户更新 appName，不直接 copy 全对象，避免越权修改 userId、priority 等敏感字段。
        App app = new App();
        app.setId(id);
        app.setAppName(appUpdateRequest.getAppName());
        // 普通用户可以调整自己应用的公开/私有状态，但不能改 priority、userId 等敏感字段。
        app.setVisibility(appService.getValidVisibility(appUpdateRequest.getVisibility()));
        if (appUpdateRequest.getTags() != null) {
            // tags 为 null 表示本次不修改标签；空数组表示主动清空标签。
            app.setTags(appService.formatTags(appUpdateRequest.getTags()));
        }
        // 设置编辑时间，方便前端展示最近修改时间。
        app.setEditTime(LocalDateTime.now());
        boolean result = appService.updateById(app);
        // updateById 返回 false 通常说明数据库写入失败或记录不存在。
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 删除应用（用户只能删除自己的应用）
     *
     * @param deleteRequest 删除请求
     * @param request       请求
     * @return 删除结果
     */
    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteApp(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        // 删除操作必须显式传入 id；这里的 id 来自请求体，不是 URL。
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 删除前先拿登录用户，用于判断是不是应用 owner 或管理员。
        User loginUser = userService.getLoginUser(request);
        long id = deleteRequest.getId();
        // 判断是否存在：避免删除不存在记录时前端误以为成功。
        App oldApp = appService.getById(id);
        ThrowUtils.throwIf(oldApp == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人或管理员可删除：管理员有平台治理权限，普通用户只能管理自己的应用。
        if (!oldApp.getUserId().equals(loginUser.getId()) && !UserConstant.ADMIN_ROLE.equals(loginUser.getUserRole())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        // AppServiceImpl 重写了 removeById，因此这里会顺带删除关联的对话历史。
        boolean result = appService.removeById(id);
        return ResultUtils.success(result);
    }

    /**
     * 根据 id 获取应用详情
     *
     * @param id 应用 id
     * @return 应用详情
     */
    @GetMapping("/get/vo")
    public BaseResponse<AppVO> getAppVOById(long id, HttpServletRequest request) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        // 查询数据库原始实体。
        App app = appService.getById(id);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR);
        // 详情页支持未登录用户查看公开应用，所以这里静默获取登录态，未登录时返回 null。
        User loginUser = userService.getLoginUserSilently(request);
        // 私有应用只能被作者或管理员查看，公开应用所有人可见。
        ThrowUtils.throwIf(!appService.isAppVisibleToUser(app, loginUser), ErrorCode.NO_AUTH_ERROR, "无权查看该应用");
        // 转成 VO 后再返回：VO 会带上用户脱敏信息，比直接返回实体更适合前端。
        return ResultUtils.success(appService.getAppVO(app));
    }

    /**
     * 分页获取当前用户创建的应用列表
     *
     * @param appQueryRequest 查询请求
     * @param request         请求
     * @return 应用列表
     */
    @PostMapping("/my/list/page/vo")
    public BaseResponse<Page<AppVO>> listMyAppVOByPage(@RequestBody AppQueryRequest appQueryRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(appQueryRequest == null, ErrorCode.PARAMS_ERROR);
        // “我的应用”依赖当前登录用户，不能让前端自己传 userId，否则可被伪造。
        User loginUser = userService.getLoginUser(request);
        // 限制每页最多 20 个，防止一次请求拉太多数据拖垮接口。
        long pageSize = appQueryRequest.getPageSize();
        ThrowUtils.throwIf(pageSize > 20, ErrorCode.PARAMS_ERROR, "每页最多查询 20 个应用");
        long pageNum = appQueryRequest.getPageNum();
        // 强制设置 userId 为当前用户，实现后端兜底权限控制。
        appQueryRequest.setUserId(loginUser.getId());
        // QueryWrapper 是 MyBatis-Flex 的查询条件构造器，由 service 统一封装。
        QueryWrapper queryWrapper = appService.getQueryWrapper(appQueryRequest);
        Page<App> appPage = appService.page(Page.of(pageNum, pageSize), queryWrapper);
        // 数据封装：实体 Page<App> 转成前端需要的 Page<AppVO>。
        Page<AppVO> appVOPage = new Page<>(pageNum, pageSize, appPage.getTotalRow());
        List<AppVO> appVOList = appService.getAppVOList(appPage.getRecords());
        appVOPage.setRecords(appVOList);
        return ResultUtils.success(appVOPage);
    }

    /**
     * 分页获取公开应用列表
     *
     * @param appQueryRequest 查询请求
     * @return 公开应用列表
     */
    @PostMapping("/list/page/vo")
    public BaseResponse<Page<AppVO>> listPublicAppVOByPage(@RequestBody AppQueryRequest appQueryRequest) {
        ThrowUtils.throwIf(appQueryRequest == null, ErrorCode.PARAMS_ERROR);
        long pageSize = appQueryRequest.getPageSize();
        ThrowUtils.throwIf(pageSize > 20, ErrorCode.PARAMS_ERROR, "每页最多查询 20 个应用");
        long pageNum = appQueryRequest.getPageNum();
        // 公开列表强制覆盖 visibility，防止前端传参绕过可见范围限制。
        appQueryRequest.setVisibility(AppVisibilityEnum.PUBLIC.getValue());
        QueryWrapper queryWrapper = appService.getQueryWrapper(appQueryRequest);
        Page<App> appPage = appService.page(Page.of(pageNum, pageSize), queryWrapper);
        Page<AppVO> appVOPage = new Page<>(pageNum, pageSize, appPage.getTotalRow());
        List<AppVO> appVOList = appService.getAppVOList(appPage.getRecords());
        appVOPage.setRecords(appVOList);
        return ResultUtils.success(appVOPage);
    }

    /**
     * 分页获取精选应用列表
     *
     * @param appQueryRequest 查询请求
     * @return 精选应用列表
     */
    @PostMapping("/good/list/page/vo")
    @Cacheable(
            // 缓存精选应用分页查询结果：缓存名为 good_app_page，
            // 使用 appQueryRequest 生成唯一缓存 Key，仅缓存前 10 页的数据
            value = "good_app_page",
            key = "T(com.yu.aicodeGeneration.utils.CacheKeyUtils).generateKey(#appQueryRequest)",
            condition = "#appQueryRequest.pageNum <= 10"
    )
    public BaseResponse<Page<AppVO>> listGoodAppVOByPage(@RequestBody AppQueryRequest appQueryRequest) {
        ThrowUtils.throwIf(appQueryRequest == null, ErrorCode.PARAMS_ERROR);
        // 限制每页最多 20 个：精选列表可能被大量访问，更要控制单次查询成本。
        long pageSize = appQueryRequest.getPageSize();
        ThrowUtils.throwIf(pageSize > 20, ErrorCode.PARAMS_ERROR, "每页最多查询 20 个应用");
        long pageNum = appQueryRequest.getPageNum();
        // 精选应用通过 priority 字段筛选，前端不需要知道具体优先级常量。
        appQueryRequest.setPriority(AppConstant.GOOD_APP_PRIORITY);
        appQueryRequest.setVisibility(AppVisibilityEnum.PUBLIC.getValue());
        QueryWrapper queryWrapper = appService.getQueryWrapper(appQueryRequest);
        // 分页查询数据库，结果会被 @Cacheable 缓存，减少首页反复查询压力。
        Page<App> appPage = appService.page(Page.of(pageNum, pageSize), queryWrapper);
        // 数据封装为 VO，补齐创建用户信息。
        Page<AppVO> appVOPage = new Page<>(pageNum, pageSize, appPage.getTotalRow());
        List<AppVO> appVOList = appService.getAppVOList(appPage.getRecords());
        appVOPage.setRecords(appVOList);
        return ResultUtils.success(appVOPage);
    }

    /**
     * 管理员删除应用
     *
     * @param deleteRequest 删除请求
     * @return 删除结果
     */
    @PostMapping("/admin/delete")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> deleteAppByAdmin(@RequestBody DeleteRequest deleteRequest) {
        // 管理员接口仍然要做参数校验；权限注解只负责角色，不负责参数正确性。
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        long id = deleteRequest.getId();
        // 删除前确认记录存在，给出更明确的错误信息。
        App oldApp = appService.getById(id);
        ThrowUtils.throwIf(oldApp == null, ErrorCode.NOT_FOUND_ERROR);
        // 同样会触发 AppServiceImpl.removeById 中的级联删除对话历史逻辑。
        boolean result = appService.removeById(id);
        return ResultUtils.success(result);
    }

    /**
     * 管理员更新应用
     *
     * @param appAdminUpdateRequest 更新请求
     * @return 更新结果
     */
    @PostMapping("/admin/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateAppByAdmin(@RequestBody AppAdminUpdateRequest appAdminUpdateRequest) {
        // 管理员更新必须指定应用 id。
        if (appAdminUpdateRequest == null || appAdminUpdateRequest.getId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        long id = appAdminUpdateRequest.getId();
        // 先查旧数据，避免对不存在的应用执行更新。
        App oldApp = appService.getById(id);
        ThrowUtils.throwIf(oldApp == null, ErrorCode.NOT_FOUND_ERROR);
        // 管理员更新字段更多，所以这里允许 copy 管理员专用 DTO 中的字段。
        App app = new App();
        BeanUtil.copyProperties(appAdminUpdateRequest, app, "visibility", "tags");
        // 管理员也复用相同的可见范围和标签校验规则，避免后台写入非法值。
        app.setVisibility(appService.getValidVisibility(appAdminUpdateRequest.getVisibility()));
        if (appAdminUpdateRequest.getTags() != null) {
            app.setTags(appService.formatTags(appAdminUpdateRequest.getTags()));
        }
        // 统一刷新编辑时间。
        app.setEditTime(LocalDateTime.now());
        boolean result = appService.updateById(app);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 管理员分页获取应用列表
     *
     * @param appQueryRequest 查询请求
     * @return 应用列表
     */
    @PostMapping("/admin/list/page/vo")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<AppVO>> listAppVOByPageByAdmin(@RequestBody AppQueryRequest appQueryRequest) {
        ThrowUtils.throwIf(appQueryRequest == null, ErrorCode.PARAMS_ERROR);
        // 管理员列表不强制 userId，因此可以按任意条件查看全站应用。
        long pageNum = appQueryRequest.getPageNum();
        long pageSize = appQueryRequest.getPageSize();
        QueryWrapper queryWrapper = appService.getQueryWrapper(appQueryRequest);
        Page<App> appPage = appService.page(Page.of(pageNum, pageSize), queryWrapper);
        // 数据封装：返回 VO 而不是实体，保持接口响应格式一致。
        Page<AppVO> appVOPage = new Page<>(pageNum, pageSize, appPage.getTotalRow());
        List<AppVO> appVOList = appService.getAppVOList(appPage.getRecords());
        appVOPage.setRecords(appVOList);
        return ResultUtils.success(appVOPage);
    }

    /**
     * 管理员根据 id 获取应用详情
     *
     * @param id 应用 id
     * @return 应用详情
     */
    @GetMapping("/admin/get/vo")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<AppVO> getAppVOByIdByAdmin(long id) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        // 管理员详情查询：仍然先查实体，再转 VO。
        App app = appService.getById(id);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR);
        // 获取封装类，保持与普通详情接口返回结构一致。
        return ResultUtils.success(appService.getAppVO(app));
    }

}
