package com.yu.aicodeGeneration.controller;

import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.yu.aicodeGeneration.annotation.AuthCheck;
import com.yu.aicodeGeneration.common.BaseResponse;
import com.yu.aicodeGeneration.common.ResultUtils;
import com.yu.aicodeGeneration.constant.UserConstant;
import com.yu.aicodeGeneration.exception.ErrorCode;
import com.yu.aicodeGeneration.exception.ThrowUtils;
import com.yu.aicodeGeneration.model.dto.chathistory.ChatHistoryQueryRequest;
import com.yu.aicodeGeneration.model.entity.ChatHistory;
import com.yu.aicodeGeneration.model.entity.User;
import com.yu.aicodeGeneration.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import com.yu.aicodeGeneration.service.ChatHistoryService;

import java.time.LocalDateTime;

/**
 * 对话历史 控制层。
 *
 * 学习重点：
 * 这里提供前端聊天页加载历史记录的接口，也提供管理员查看全量对话记录的接口。
 * 普通用户只能看自己应用的历史，权限校验放在 ChatHistoryService 中。
 */
@RestController
@RequestMapping("/chatHistory")
public class ChatHistoryController {

    // 对话历史服务：负责游标分页、权限校验和查询条件构造。
    @Resource
    private ChatHistoryService chatHistoryService;

    // 用户服务：用于获取当前登录用户。
    @Resource
    private UserService userService;

    /**
     * 分页查询某个应用的对话历史（游标查询）
     *
     * @param appId          应用ID
     * @param pageSize       页面大小
     * @param lastCreateTime 最后一条记录的创建时间
     * @param request        请求
     * @return 对话历史分页
     */
    @GetMapping("/app/{appId}")
    public BaseResponse<Page<ChatHistory>> listAppChatHistory(@PathVariable Long appId,
                                                              @RequestParam(defaultValue = "10") int pageSize,
                                                              @RequestParam(required = false) LocalDateTime lastCreateTime,
                                                              HttpServletRequest request) {
        // 先获取登录用户，service 会判断这个用户是否有权查看该 app 的对话历史。
        User loginUser = userService.getLoginUser(request);
        // 使用 lastCreateTime 做游标：加载更多时查询更早的消息。
        Page<ChatHistory> result = chatHistoryService.listAppChatHistoryByPage(appId, pageSize, lastCreateTime, loginUser);
        return ResultUtils.success(result);
    }

    /**
     * 管理员分页查询所有对话历史
     *
     * @param chatHistoryQueryRequest 查询请求
     * @return 对话历史分页
     */
    @PostMapping("/admin/list/page/vo")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<ChatHistory>> listAllChatHistoryByPageForAdmin(@RequestBody ChatHistoryQueryRequest chatHistoryQueryRequest) {
        // 管理员查询也需要请求体，里面包含分页和筛选条件。
        ThrowUtils.throwIf(chatHistoryQueryRequest == null, ErrorCode.PARAMS_ERROR);
        long pageNum = chatHistoryQueryRequest.getPageNum();
        long pageSize = chatHistoryQueryRequest.getPageSize();
        // 查询数据：管理员接口不限制 app owner，可以按任意条件查。
        QueryWrapper queryWrapper = chatHistoryService.getQueryWrapper(chatHistoryQueryRequest);
        Page<ChatHistory> result = chatHistoryService.page(Page.of(pageNum, pageSize), queryWrapper);
        return ResultUtils.success(result);
    }
}
