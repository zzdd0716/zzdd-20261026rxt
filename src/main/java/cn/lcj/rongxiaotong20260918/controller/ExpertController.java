package cn.lcj.rongxiaotong20260918.controller;

import cn.lcj.rongxiaotong20260918.common.PageResult;
import cn.lcj.rongxiaotong20260918.common.Result;
import cn.lcj.rongxiaotong20260918.pojo.dto.ExpertProfileDTO;
import cn.lcj.rongxiaotong20260918.pojo.vo.ExpertDateSlotsVO;
import cn.lcj.rongxiaotong20260918.pojo.vo.ExpertDetailVO;
import cn.lcj.rongxiaotong20260918.pojo.vo.ExpertListVO;
import cn.lcj.rongxiaotong20260918.pojo.vo.ExpertProfileVO;
import cn.lcj.rongxiaotong20260918.service.ExpertService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 专家模块（后端 B）
 * 接口文档：02-backend-B-trade-expert.md 第五章
 *
 * 注意：对外"专家ID"为 user.id（与 appointment.expert_id 一致）；
 * 5.1/5.2/5.5 为公开接口，5.3/5.4 需登录（当前用户必须是专家）。
 */
@RestController
@RequestMapping("/api/expert")
public class ExpertController extends BaseController {

    @Autowired
    private ExpertService expertService;

    /** 5.1 专家列表（公开） */
    @GetMapping
    public Result<PageResult<ExpertListVO>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false, defaultValue = "1") Integer pageNum,
            @RequestParam(required = false, defaultValue = "10") Integer pageSize) {
        return Result.success(expertService.list(keyword, pageNum, pageSize));
    }

    /** 5.2 专家详情（公开，含未来14天忙碌时段） */
    @GetMapping("/{id}")
    public Result<ExpertDetailVO> detail(@PathVariable Long id) {
        return Result.success(expertService.detail(id));
    }

    /** 5.3 专家自己的资料（需登录且为专家） */
    @GetMapping("/profile")
    public Result<ExpertProfileVO> profile() {
        return Result.success(expertService.profile(getCurrentUserId()));
    }

    /** 5.4 专家完善自己的资料（需登录且为专家） */
    @PutMapping("/profile")
    public Result<Void> updateProfile(@RequestBody ExpertProfileDTO dto) {
        expertService.updateProfile(getCurrentUserId(), dto);
        return Result.success();
    }

    /** 5.5 专家可预约时间（公开，未来14天，含冲突检测） */
    @GetMapping("/{id}/available-time")
    public Result<List<ExpertDateSlotsVO>> availableTime(@PathVariable Long id) {
        return Result.success(expertService.availableTime(id));
    }
}
