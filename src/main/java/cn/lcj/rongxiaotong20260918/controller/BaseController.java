package cn.lcj.rongxiaotong20260918.controller;

import cn.lcj.rongxiaotong20260918.mapper.UserMapper;
import cn.lcj.rongxiaotong20260918.pojo.entity.User;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 所有需要"当前登录用户"的 Controller 的父类。
 *
 * 开发期（无 Spring Security）：从请求头 X-User-Id 读取用户ID（Apifox 里手动带上）。
 * 生产期（启用 Spring Security + JWT）：切到 SecurityContextHolder（只改这一个类）。
 *
 * 不需要当前用户的 Controller（如 LoginController、公共分类查询）可以不继承此类。
 */
public abstract class BaseController {

    @Autowired
    protected UserMapper userMapper;

    /**
     * 获取当前用户ID
     */
    protected Long getCurrentUserId() {
        HttpServletRequest request = getRequest();
        if (request == null) return null;
        String uid = request.getHeader("X-User-Id");
        if (uid == null || uid.isBlank()) return null;
        try {
            return Long.valueOf(uid);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 获取当前用户完整信息（查数据库）
     */
    protected User getCurrentUser() {
        Long uid = getCurrentUserId();
        return uid == null ? null : userMapper.selectById(uid);
    }

    private HttpServletRequest getRequest() {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attrs == null ? null : attrs.getRequest();
    }
}
