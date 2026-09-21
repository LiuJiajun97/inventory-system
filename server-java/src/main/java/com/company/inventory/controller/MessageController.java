package com.company.inventory.controller;

import com.company.inventory.common.exception.BizException;
import com.company.inventory.common.page.PageResult;
import com.company.inventory.config.JwtInterceptor;
import com.company.inventory.model.vo.log.SysMessageVO;
import com.company.inventory.service.MessageService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 站内消息接口(V25):登录即可访问自己的消息(无 @RequirePerm,登录态即权限)。
 *
 * @author inventory
 */
@Tag(name = "站内消息")
@RestController
@RequestMapping("/api/v1/messages")
@Validated
public class MessageController {

    /** 消息服务。 */
    private final MessageService messageService;

    /**
     * 构造控制器。
     *
     * @param messageService 消息服务
     */
    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    /**
     * 当前用户消息分页列表(时间倒序)。
     *
     * @param unread 是否仅未读(可空)
     * @param page   页码
     * @param size   页大小
     * @param request 请求(取当前用户 ID)
     * @return 分页结果
     */
    @Operation(summary = "站内消息列表")
    @GetMapping
    public PageResult<SysMessageVO> list(@RequestParam(required = false) Boolean unread,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {
        int userId = requireUserId(request);
        return messageService.list(unread, page, size, userId);
    }

    /**
     * 当前用户未读消息数(顶栏铃铛用)。
     *
     * @param request 请求(取当前用户 ID)
     * @return 未读数量
     */
    @Operation(summary = "未读消息数")
    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount(HttpServletRequest request) {
        int userId = requireUserId(request);
        return Map.of("count", messageService.unreadCount(userId));
    }

    /**
     * 标记已读:id 有值单条已读,无 id 全部已读。
     *
     * @param body    请求体 {id: number?}
     * @param request 请求(取当前用户 ID)
     * @return 已标记条数
     */
    @Operation(summary = "标记消息已读")
    @PutMapping("/read")
    public Map<String, Integer> markRead(@RequestBody(required = false) Map<String, Long> body,
            HttpServletRequest request) {
        int userId = requireUserId(request);
        Long id = body == null ? null : body.get("id");
        return Map.of("count", messageService.markRead(id, userId));
    }

    /**
     * 每日预警汇总(dashboard 挂载调用)。
     *
     * @param request 请求(取当前用户 ID)
     * @return 已生成或已存在的那条消息(预警 0 项返回 200 + body.content=null)
     */
    @Operation(summary = "每日预警汇总")
    @PostMapping("/daily-alert")
    public SysMessageVO dailyAlert(HttpServletRequest request) {
        int userId = requireUserId(request);
        return messageService.dailyAlert(userId);
    }

    /**
     * 取当前登录用户 ID(未登录抛 401)。
     *
     * @param request 请求
     * @return 用户 ID
     */
    private int requireUserId(HttpServletRequest request) {
        Object attr = request.getAttribute(JwtInterceptor.ATTR_USER_ID);
        if (attr == null) {
            throw BizException.unauthorized("未识别用户");
        }
        return ((Long) attr).intValue();
    }
}