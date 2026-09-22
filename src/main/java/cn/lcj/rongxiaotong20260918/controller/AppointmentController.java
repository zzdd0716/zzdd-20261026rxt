package cn.lcj.rongxiaotong20260918.controller;

import cn.lcj.rongxiaotong20260918.common.Result;
import cn.lcj.rongxiaotong20260918.pojo.dto.AppointmentDTO;
import cn.lcj.rongxiaotong20260918.pojo.vo.AppointmentCreateVO;
import cn.lcj.rongxiaotong20260918.pojo.vo.AppointmentVO;
import cn.lcj.rongxiaotong20260918.service.AppointmentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 专家预约模块（后端 B）
 * 接口文档：02-backend-B-trade-expert.md 第七章
 * 全部接口需登录。
 */
@RestController
@RequestMapping("/api/appointment")
public class AppointmentController extends BaseController {

    @Autowired
    private AppointmentService appointmentService;

    /** 7.1 创建预约（需登录，含时间冲突检测） */
    @PostMapping
    public Result<AppointmentCreateVO> create(@RequestBody AppointmentDTO dto) {
        return Result.success(appointmentService.create(getCurrentUserId(), dto));
    }

    /** 7.2 用户端：我的预约（需登录） */
    @GetMapping("/my/user")
    public Result<List<AppointmentVO>> myUserList(
            @RequestParam(required = false) Integer status) {
        return Result.success(appointmentService.myUserList(getCurrentUserId(), status));
    }

    /** 7.3 专家端：我的预约（需登录且为专家） */
    @GetMapping("/my/expert")
    public Result<List<AppointmentVO>> myExpertList(
            @RequestParam(required = false) Integer status) {
        return Result.success(appointmentService.myExpertList(getCurrentUserId(), status));
    }

    /** 7.4 专家确认预约（仅待确认） */
    @PutMapping("/{id}/confirm")
    public Result<Void> confirm(@PathVariable Long id) {
        appointmentService.confirm(getCurrentUserId(), id);
        return Result.success();
    }

    /** 7.5 专家标记完成（仅已确认） */
    @PutMapping("/{id}/complete")
    public Result<Void> complete(@PathVariable Long id) {
        appointmentService.complete(getCurrentUserId(), id);
        return Result.success();
    }

    /** 7.6 取消预约（创建者，仅待确认） */
    @PutMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id) {
        appointmentService.cancel(getCurrentUserId(), id);
        return Result.success();
    }
}
