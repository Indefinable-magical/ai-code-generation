package com.yu.aicodeGeneration.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.yu.aicodeGeneration.ai.AiCodeGenTypeRoutingService;
import com.yu.aicodeGeneration.ai.AiCodeGenTypeRoutingServiceFactory;
import com.yu.aicodeGeneration.constant.AppConstant;
import com.yu.aicodeGeneration.constant.UserConstant;
import com.yu.aicodeGeneration.core.AiCodeGeneratorFacade;
import com.yu.aicodeGeneration.core.builder.VueProjectBuilder;
import com.yu.aicodeGeneration.core.handler.StreamHandlerExecutor;
import com.yu.aicodeGeneration.exception.BusinessException;
import com.yu.aicodeGeneration.exception.ErrorCode;
import com.yu.aicodeGeneration.exception.ThrowUtils;
import com.yu.aicodeGeneration.manager.GenerationTaskManager;
import com.yu.aicodeGeneration.model.dto.app.AppAddRequest;
import com.yu.aicodeGeneration.model.dto.app.AppQueryRequest;
import com.yu.aicodeGeneration.model.entity.App;
import com.yu.aicodeGeneration.mapper.AppMapper;
import com.yu.aicodeGeneration.model.entity.User;
import com.yu.aicodeGeneration.model.enums.AppVisibilityEnum;
import com.yu.aicodeGeneration.model.enums.ChatHistoryMessageTypeEnum;
import com.yu.aicodeGeneration.model.enums.CodeGenTypeEnum;
import com.yu.aicodeGeneration.model.vo.AppVO;
import com.yu.aicodeGeneration.model.vo.UserVO;
import com.yu.aicodeGeneration.monitor.MonitorContext;
import com.yu.aicodeGeneration.monitor.MonitorContextHolder;
import com.yu.aicodeGeneration.service.AppService;
import com.yu.aicodeGeneration.service.ChatHistoryService;
import com.yu.aicodeGeneration.service.ScreenshotService;
import com.yu.aicodeGeneration.service.UserService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.io.File;
import java.io.Serializable;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 应用服务层实现。
 *
 * 学习重点：
 * 1. Service 是业务编排层，负责把“查库、鉴权、调用 AI、保存历史、部署文件”等动作串起来。
 * 2. Controller 不直接调用 AI，而是经由这里做权限和上下文处理后再进入核心生成模块。
 * 3. 本类的主线方法是 chatToGenCode、createApp、deployApp。
 */
@Service
@Slf4j
public class AppServiceImpl extends ServiceImpl<AppMapper, App> implements AppService {

    // 部署后的访问域名，默认是 http://localhost；线上环境可通过 code.deploy-host 覆盖。
    @Value("${code.deploy-host:http://localhost}")
    private String deployHost;

    // 用户服务：用于查询用户信息、封装用户 VO，以及辅助权限判断。
    @Resource
    private UserService userService;

    // AI 生成门面：把“选择 AI 服务、流式生成、解析保存、构建项目”封装在 core 层。
    @Resource
    private AiCodeGeneratorFacade aiCodeGeneratorFacade;

    // 对话历史服务：保存用户消息和 AI 消息，也负责把历史加载回 AI 记忆。
    @Resource
    private ChatHistoryService chatHistoryService;

    // 流处理执行器：负责在 SSE 流结束后，把 AI 回复保存到 chat_history。
    @Resource
    private StreamHandlerExecutor streamHandlerExecutor;

    // Vue 项目构建器：对 vue_project 类型执行 npm install/build。
    @Resource
    private VueProjectBuilder vueProjectBuilder;

    // 截图服务：部署完成后生成应用封面图并上传到对象存储。
    @Resource
    private ScreenshotService screenshotService;

    // AI 路由服务工厂：创建“根据 prompt 判断生成类型”的 AI 服务实例。
    @Resource
    private AiCodeGenTypeRoutingServiceFactory aiCodeGenTypeRoutingServiceFactory;

    // AI 生成任务管理器：记录当前正在生成的应用，并支持用户主动停止生成。
    @Resource
    private GenerationTaskManager generationTaskManager;

    @Override
    public Flux<String> chatToGenCode(Long appId, String message, User loginUser) {
        // 1. 参数校验：service 层再次校验，保证即便未来有其他入口调用也不会绕过规则。
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 错误");
        ThrowUtils.throwIf(StrUtil.isBlank(message), ErrorCode.PARAMS_ERROR, "提示词不能为空");
        // 2. 查询应用信息：后续需要 app.userId 做权限校验，也需要 app.codeGenType 决定生成模式。
        App app = this.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        // 3. 权限校验：AI 对话会修改应用代码，因此只允许应用创建者发起。
        if (!app.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限访问该应用");
        }
        // 4. 获取应用的代码生成类型。
        // 这个类型在 createApp 时由 AI 路由服务决定，后面会影响 prompt、解析器、保存器和构建逻辑。
        String codeGenType = app.getCodeGenType();
        CodeGenTypeEnum codeGenTypeEnum = CodeGenTypeEnum.getEnumByValue(codeGenType);
        if (codeGenTypeEnum == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "应用代码生成类型错误");
        }
        // 5. 在调用 AI 前先保存用户消息。
        // 这样即使 AI 生成失败，用户刚才提了什么也能被保留下来，方便排查和恢复上下文。
        // 注册当前应用的生成任务；如果同一个应用已有生成任务，会在这里直接拦截。
        GenerationTaskManager.GenerationTask generationTask = generationTaskManager.startGeneration(appId);
        try {
            chatHistoryService.addChatMessage(appId, message, ChatHistoryMessageTypeEnum.USER.getValue(), loginUser.getId());
            // 6. 设置监控上下文。
            // LangChain4j 的监听器里不一定能直接拿到业务参数，所以用 ThreadLocal 暂存 userId/appId。
            MonitorContextHolder.setContext(
                    MonitorContext.builder()
                            .userId(loginUser.getId().toString())
                            .appId(appId.toString())
                            .build()
            );
            // 7. 调用 AI 生成代码（流式）。
            // 这里不会一次性等 AI 完整生成，而是返回 Flux，让 Controller 可以持续推送给前端。
            Flux<String> codeStream = aiCodeGeneratorFacade.generateAndSaveCodeStream(message, codeGenTypeEnum, appId);
            // 8. 包装流处理逻辑。
            // streamHandlerExecutor 会边转发边收集内容，在流完成后把 AI 回复写入 chat_history。
            return streamHandlerExecutor.doExecute(codeStream, chatHistoryService, appId, loginUser, codeGenTypeEnum)
                    // 用户点击停止后，stopSignal 会结束当前响应流，前端也会随之停止接收 SSE。
                    .takeUntilOther(generationTaskManager.stopSignal(generationTask))
                    .doFinally(signalType -> {
                        // 无论正常完成、异常还是被取消，都要移除当前任务，避免应用一直处于“生成中”。
                        generationTaskManager.finishGeneration(appId, generationTask);
                        // 流结束时清理 ThreadLocal。
                        // doFinally 会在成功、失败、取消三种情况下都执行，避免线程复用时污染后续请求。
                        MonitorContextHolder.clearContext();
                    });
        } catch (Throwable e) {
            // 如果创建 Flux 前发生异常，也要清理任务和监控上下文。
            generationTaskManager.finishGeneration(appId, generationTask);
            MonitorContextHolder.clearContext();
            throw e;
        }
    }

    @Override
    public Boolean stopGenCode(Long appId, User loginUser) {
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 错误");
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR);
        // 停止生成前先查应用，确保 appId 真实存在，也方便做 owner 校验。
        App app = this.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        boolean isAdmin = UserConstant.ADMIN_ROLE.equals(loginUser.getUserRole());
        if (!app.getUserId().equals(loginUser.getId()) && !isAdmin) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限停止该应用生成");
        }
        return generationTaskManager.requestStop(appId);
    }

    @Override
    public Long createApp(AppAddRequest appAddRequest, User loginUser) {
        // 创建应用的唯一必要输入是初始化 prompt，后续 AI 会围绕它生成第一版应用。
        String initPrompt = appAddRequest.getInitPrompt();
        ThrowUtils.throwIf(StrUtil.isBlank(initPrompt), ErrorCode.PARAMS_ERROR, "初始化 prompt 不能为空");
        // 构造入库对象：先把 DTO 的普通字段复制到实体。
        App app = new App();
        BeanUtil.copyProperties(appAddRequest, app, "visibility", "tags");
        // 每个应用必须归属于一个用户，后续“我的应用”、权限校验都依赖 userId。
        app.setUserId(loginUser.getId());
        // 应用名称暂时取 prompt 前 12 个字符，用户后面可以编辑名称。
        app.setAppName(initPrompt.substring(0, Math.min(initPrompt.length(), 12)));
        // 使用 AI 智能选择代码生成类型。
        // 比如简单页面可以走 html，复杂工程可以走 vue_project，避免用户手动理解技术细节。
        AiCodeGenTypeRoutingService aiCodeGenTypeRoutingService = aiCodeGenTypeRoutingServiceFactory.createAiCodeGenTypeRoutingService();
        CodeGenTypeEnum selectedCodeGenType = aiCodeGenTypeRoutingService.routeCodeGenType(initPrompt);
        // 把类型落库后，后续聊天生成时直接沿用，不必每次重新路由。
        app.setCodeGenType(selectedCodeGenType.getValue());
        // 新建应用默认私有，用户主动选择公开后才会出现在公开列表。
        app.setVisibility(getValidVisibilityOrDefault(appAddRequest.getVisibility()));
        // 标签统一格式化为逗号包裹的字符串，方便后续按完整标签做 like 查询。
        app.setTags(formatTags(appAddRequest.getTags()));
        // 插入数据库后，MyBatis-Flex 会回填自增主键到 app.id。
        boolean result = this.save(app);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        log.info("应用创建成功，ID: {}, 类型: {}", app.getId(), selectedCodeGenType.getValue());
        return app.getId();
    }

    @Override
    public String deployApp(Long appId, User loginUser) {
        // 1. 参数校验：部署必须有应用 ID 且用户已登录。
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 错误");
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR, "用户未登录");
        // 2. 查询应用信息：部署需要知道代码生成类型、创建者和已有 deployKey。
        App app = this.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        // 3. 权限校验：部署会公开访问地址，因此只能部署自己的应用。
        if (!app.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限部署该应用");
        }
        // 4. 检查是否已有 deployKey。
        // 已部署过的应用复用原 deployKey，这样 URL 不会因为重复部署而变化。
        String deployKey = app.getDeployKey();
        // 如果没有，则生成 6 位 deployKey（字母 + 数字），作为部署目录名和访问路径。
        if (StrUtil.isBlank(deployKey)) {
            deployKey = RandomUtil.randomString(6);
        }
        // 5. 获取原始代码生成路径。
        // 生成目录来自保存器，命名规则是 codeGenType_appId，例如 html_1001。
        String codeGenType = app.getCodeGenType();
        String sourceDirName = codeGenType + "_" + appId;
        String sourceDirPath = AppConstant.CODE_OUTPUT_ROOT_DIR + File.separator + sourceDirName;
        // 6. 检查路径是否存在：部署依赖已经生成好的代码目录。
        File sourceDir = new File(sourceDirPath);
        if (!sourceDir.exists() || !sourceDir.isDirectory()) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "应用代码路径不存在，请先生成应用");
        }
        // 7. Vue 项目特殊处理：源码目录不能直接部署，需要先构建出 dist 静态产物。
        CodeGenTypeEnum codeGenTypeEnum = CodeGenTypeEnum.getEnumByValue(codeGenType);
        if (codeGenTypeEnum == CodeGenTypeEnum.VUE_PROJECT) {
            // buildProject 内部会执行依赖安装和打包命令，返回 false 表示构建失败。
            boolean buildSuccess = vueProjectBuilder.buildProject(sourceDirPath);
            ThrowUtils.throwIf(!buildSuccess, ErrorCode.SYSTEM_ERROR, "Vue 项目构建失败，请重试");
            // 构建成功后理论上必须存在 dist 目录；这里再兜底检查一次。
            File distDir = new File(sourceDirPath, "dist");
            ThrowUtils.throwIf(!distDir.exists(), ErrorCode.SYSTEM_ERROR, "Vue 项目构建完成但未生成 dist 目录");
            // 对 Vue 项目来说，真正需要部署的是 dist，而不是完整源码。
            sourceDir = distDir;
        }
        // 8. 复制文件到部署目录。
        // 部署目录与生成目录分离：生成目录保留源码，部署目录只服务线上/预览访问。
        String deployDirPath = AppConstant.CODE_DEPLOY_ROOT_DIR + File.separator + deployKey;
        try {
            FileUtil.copyContent(sourceDir, new File(deployDirPath), true);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "应用部署失败：" + e.getMessage());
        }
        // 9. 更新数据库，记录 deployKey 和最近部署时间。
        App updateApp = new App();
        updateApp.setId(appId);
        updateApp.setDeployKey(deployKey);
        updateApp.setDeployedTime(LocalDateTime.now());
        boolean updateResult = this.updateById(updateApp);
        ThrowUtils.throwIf(!updateResult, ErrorCode.OPERATION_ERROR, "更新应用部署信息失败");
        // 10. 构建应用访问 URL：deployHost 是域名，deployKey 是静态资源目录。
        String appDeployUrl = String.format("%s/%s/", deployHost, deployKey);
        // 11. 异步生成截图并且更新应用封面，不阻塞当前部署接口返回。
        generateAppScreenshotAsync(appId, appDeployUrl);
        return appDeployUrl;
    }

    /**
     * 异步生成应用截图并更新封面
     *
     * @param appId  应用ID
     * @param appUrl 应用访问URL
     */
    @Override
    public void generateAppScreenshotAsync(Long appId, String appUrl) {
        // 使用虚拟线程执行截图任务。
        // 截图包含浏览器启动、页面加载、上传对象存储，耗时较长，不适合阻塞部署接口。
        Thread.startVirtualThread(() -> {
            // 调用截图服务生成截图并上传，返回的是可访问的图片 URL。
            String screenshotUrl = screenshotService.generateAndUploadScreenshot(appUrl);
            // 更新数据库的封面字段；前端首页列表会用 cover 展示应用卡片。
            App updateApp = new App();
            updateApp.setId(appId);
            updateApp.setCover(screenshotUrl);
            boolean updated = this.updateById(updateApp);
            ThrowUtils.throwIf(!updated, ErrorCode.OPERATION_ERROR, "更新应用封面字段失败");
        });
    }

    @Override
    public AppVO getAppVO(App app) {
        // 入参为空时直接返回 null，调用方可以自行决定如何处理。
        if (app == null) {
            return null;
        }
        // AppVO 是给前端看的对象，避免直接暴露数据库实体的所有字段。
        AppVO appVO = new AppVO();
        BeanUtil.copyProperties(app, appVO, "tags");
        // 数据库存储的是字符串，返回前端时转成数组，便于组件直接渲染和编辑。
        appVO.setTags(parseTags(app.getTags()));
        appVO.setVisibility(getValidVisibilityOrDefault(app.getVisibility()));
        // 关联查询用户信息，让前端展示创建者昵称、头像等。
        Long userId = app.getUserId();
        if (userId != null) {
            User user = userService.getById(userId);
            UserVO userVO = userService.getUserVO(user);
            appVO.setUser(userVO);
        }
        return appVO;
    }

    @Override
    public List<AppVO> getAppVOList(List<App> appList) {
        // 空集合直接返回空列表，避免调用方空指针。
        if (CollUtil.isEmpty(appList)) {
            return new ArrayList<>();
        }
        // 批量获取用户信息，避免 N+1 查询问题。
        // 如果每个 app 都单独 getById，一页 20 条就会额外查 20 次用户表。
        Set<Long> userIds = appList.stream()
                .map(App::getUserId)
                .collect(Collectors.toSet());
        // 把用户列表转成 Map，后面可以 O(1) 按 userId 找到用户 VO。
        Map<Long, UserVO> userVOMap = userService.listByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, userService::getUserVO));
        return appList.stream().map(app -> {
            // 先复用单个 App 的 VO 封装逻辑，再用批量查到的 userVO 覆盖，保证用户信息来自批量查询。
            AppVO appVO = getAppVO(app);
            UserVO userVO = userVOMap.get(app.getUserId());
            appVO.setUser(userVO);
            return appVO;
        }).collect(Collectors.toList());
    }

    @Override
    public QueryWrapper getQueryWrapper(AppQueryRequest appQueryRequest) {
        // 查询条件对象不能为空，否则无法知道分页、排序和过滤参数。
        if (appQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        // 先把 DTO 字段拆出来，便于后面构造查询条件，也让每个字段含义更清楚。
        Long id = appQueryRequest.getId();
        String appName = appQueryRequest.getAppName();
        String cover = appQueryRequest.getCover();
        String initPrompt = appQueryRequest.getInitPrompt();
        String codeGenType = appQueryRequest.getCodeGenType();
        String deployKey = appQueryRequest.getDeployKey();
        Integer priority = appQueryRequest.getPriority();
        String visibility = StrUtil.blankToDefault(appQueryRequest.getVisibility(), null);
        String tag = appQueryRequest.getTag();
        List<String> tags = appQueryRequest.getTags();
        Long userId = appQueryRequest.getUserId();
        String sortField = appQueryRequest.getSortField();
        String sortOrder = appQueryRequest.getSortOrder();
        // MyBatis-Flex 的 QueryWrapper 会忽略空值条件，所以可以直接把可能为 null 的字段传进去。
        // like 用于模糊搜索，eq 用于精确匹配，orderBy 根据前端传入的排序字段和顺序排序。
        QueryWrapper queryWrapper = QueryWrapper.create()
                .eq("id", id)
                .like("appName", appName)
                .like("cover", cover)
                .like("initPrompt", initPrompt)
                .eq("codeGenType", codeGenType)
                .eq("deployKey", deployKey)
                .eq("priority", priority)
                .eq("visibility", visibility)
                .eq("userId", userId)
                .orderBy(sortField, "ascend".equals(sortOrder));
        if (StrUtil.isNotBlank(tag)) {
            // 标签以 ,tag, 形式存储和查询，避免 tag 匹配到 tag2 这类前缀误命中。
            queryWrapper.like("tags", formatTagForQuery(tag));
        }
        if (CollUtil.isNotEmpty(tags)) {
            for (String item : tags) {
                if (StrUtil.isNotBlank(item)) {
                    // 多标签筛选采用 AND 关系，只有同时包含这些标签的应用才会返回。
                    queryWrapper.like("tags", formatTagForQuery(item));
                }
            }
        }
        return queryWrapper;
    }

    @Override
    public boolean isAppVisibleToUser(App app, User loginUser) {
        if (app == null) {
            return false;
        }
        // 公开应用任何人都能看；私有应用继续走登录用户权限判断。
        if (AppVisibilityEnum.PUBLIC.getValue().equals(app.getVisibility())) {
            return true;
        }
        if (loginUser == null) {
            return false;
        }
        return app.getUserId().equals(loginUser.getId())
                || UserConstant.ADMIN_ROLE.equals(loginUser.getUserRole());
    }

    @Override
    public String getValidVisibility(String visibility) {
        if (StrUtil.isBlank(visibility)) {
            // 返回 null 表示本次更新不改 visibility 字段，避免把空值写入数据库。
            return null;
        }
        AppVisibilityEnum visibilityEnum = AppVisibilityEnum.getEnumByValue(visibility);
        ThrowUtils.throwIf(visibilityEnum == null, ErrorCode.PARAMS_ERROR, "应用可见范围错误");
        return visibility;
    }

    private String getValidVisibilityOrDefault(String visibility) {
        if (StrUtil.isBlank(visibility)) {
            // 老数据或未传参时统一按私有处理，保护用户隐私。
            return AppVisibilityEnum.PRIVATE.getValue();
        }
        return getValidVisibility(visibility);
    }

    @Override
    public String formatTags(List<String> tagList) {
        if (CollUtil.isEmpty(tagList)) {
            return "";
        }
        LinkedHashSet<String> tagSet = new LinkedHashSet<>();
        for (String tag : tagList) {
            if (StrUtil.isBlank(tag)) {
                continue;
            }
            String cleanTag = tag.trim();
            // 标签直接用于模糊查询，限制长度和逗号可以降低脏数据和误匹配风险。
            ThrowUtils.throwIf(cleanTag.length() > 20, ErrorCode.PARAMS_ERROR, "单个标签不能超过 20 个字符");
            ThrowUtils.throwIf(cleanTag.contains(","), ErrorCode.PARAMS_ERROR, "标签不能包含英文逗号");
            tagSet.add(cleanTag);
            ThrowUtils.throwIf(tagSet.size() > 10, ErrorCode.PARAMS_ERROR, "最多设置 10 个标签");
        }
        if (tagSet.isEmpty()) {
            return "";
        }
        // 前后各补一个逗号，查询时用 ,标签, 精确匹配完整标签。
        return "," + String.join(",", tagSet) + ",";
    }

    private List<String> parseTags(String tags) {
        if (StrUtil.isBlank(tags)) {
            return new ArrayList<>();
        }
        // 过滤空字符串，兼容前后逗号和历史上可能存在的重复分隔符。
        return Arrays.stream(tags.split(","))
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.toList());
    }

    private String formatTagForQuery(String tag) {
        return "," + tag.trim() + ",";
    }

    /**
     * 删除应用时，关联删除对话历史
     *
     * @param id
     * @return
     */
    @Override
    public boolean removeById(Serializable id) {
        // 防御性校验：没有 id 就无法删除。
        if (id == null) {
            return false;
        }
        // IService 的 removeById 接收 Serializable，这里统一转成 long 方便校验和删除关联数据。
        long appId = Long.parseLong(id.toString());
        if (appId <= 0) {
            return false;
        }
        App app = this.getById(appId);
        // 删除前先发停止信号，防止生成流继续向即将被删除的目录写文件。
        generationTaskManager.requestStop(appId);
        // 先删除关联的对话历史。
        // 这里即使删除历史失败，也继续删除应用；因为历史删除失败不应该完全阻塞应用删除。
        try {
            chatHistoryService.deleteByAppId(appId);
        } catch (Exception e) {
            log.error("删除应用关联的对话历史失败：{}", e.getMessage());
        }
        // 删除应用时同步清理生成产物和部署目录，避免无主文件长期堆积。
        try {
            cleanupAppFiles(app);
        } catch (Exception e) {
            log.error("清理应用关联文件失败：{}", e.getMessage());
        }
        // 最后调用父类真正删除 app 表记录。
        return super.removeById(id);
    }

    private void cleanupAppFiles(App app) {
        if (app == null) {
            return;
        }
        Long appId = app.getId();
        String codeGenType = app.getCodeGenType();
        if (appId != null && StrUtil.isNotBlank(codeGenType)) {
            // 生成目录命名规则和 CodeFileSaverTemplate 保持一致：类型_appId。
            String sourceDirName = codeGenType + "_" + appId;
            deleteDirectoryUnderRoot(AppConstant.CODE_OUTPUT_ROOT_DIR, sourceDirName);
        }
        String deployKey = app.getDeployKey();
        if (StrUtil.isNotBlank(deployKey)) {
            // 部署目录按 deployKey 命名，删除应用时一并下线静态产物。
            deleteDirectoryUnderRoot(AppConstant.CODE_DEPLOY_ROOT_DIR, deployKey);
        }
    }

    private void deleteDirectoryUnderRoot(String rootDirPath, String dirName) {
        if (StrUtil.hasBlank(rootDirPath, dirName)) {
            return;
        }
        File rootDir = new File(rootDirPath);
        File targetDir = new File(rootDir, dirName);
        Path rootPath = rootDir.toPath().toAbsolutePath().normalize();
        Path targetPath = targetDir.toPath().toAbsolutePath().normalize();
        // 删除文件前做路径归一化校验，确保目标目录没有逃逸出指定根目录。
        if (!targetPath.startsWith(rootPath)) {
            log.warn("跳过越界目录清理: {}", targetPath);
            return;
        }
        if (targetDir.exists()) {
            FileUtil.del(targetDir);
        }
    }
}
