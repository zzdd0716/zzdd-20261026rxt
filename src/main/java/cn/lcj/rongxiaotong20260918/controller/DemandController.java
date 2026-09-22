package cn.lcj.rongxiaotong20260918.controller;

import cn.lcj.rongxiaotong20260918.common.PageResult;
import cn.lcj.rongxiaotong20260918.common.Result;
import cn.lcj.rongxiaotong20260918.pojo.dto.DemandDTO;
import cn.lcj.rongxiaotong20260918.pojo.entity.Demand;
import cn.lcj.rongxiaotong20260918.pojo.vo.DemandDetailVO;
import cn.lcj.rongxiaotong20260918.pojo.vo.DemandListVO;
import cn.lcj.rongxiaotong20260918.service.DemandService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 采购需求模块（后端 B）
 * 接口文档：02-backend-B-trade-expert.md 第二章
 *
 * 权限说明（开发期无 Spring Security，当前用户从请求头 X-User-Id 读取；
 * 生产期启用 JWT 后由 SecurityConfig 拦截，需登录接口自动生效）：
 * - 公开：列表、详情
 * - 需登录：发布、修改、删除、我的需求
 */
@RestController
@RequestMapping("/api/demand")
public class DemandController extends BaseController {

    @Autowired
    private DemandService demandService;

    /** 2.1 需求列表（公开） */
    @GetMapping
    public Result<PageResult<DemandListVO>> pageList(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false, defaultValue = "1") Integer pageNum,
            @RequestParam(required = false, defaultValue = "10") Integer pageSize) {
        return Result.success(demandService.pageList(keyword, categoryId, status, pageNum, pageSize));
    }

    /** 2.2 需求详情（公开） */
    @GetMapping("/{id}")
    public Result<DemandDetailVO> getDetail(@PathVariable Long id) {
        return Result.success(demandService.getDetail(id));
    }

    /** 2.3 发布需求（需登录） */
    @PostMapping
    public Result<Map<String, Long>> create(@RequestBody DemandDTO dto) {
        Long id = demandService.create(getCurrentUserId(), dto);
        return Result.success(Map.of("id", id));
    }

    /** 2.4 修改需求（需登录） */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody DemandDTO dto) {
        demandService.update(getCurrentUserId(), id, dto);
        return Result.success();
    }

    /** 2.5 删除需求（需登录） */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        demandService.delete(getCurrentUserId(), id);
        return Result.success();
    }

    /** 2.6 我的需求（需登录） */
    @GetMapping("/my")
    public Result<PageResult<Demand>> myList(
            @RequestParam(required = false, defaultValue = "1") Integer pageNum,
            @RequestParam(required = false, defaultValue = "10") Integer pageSize) {
        return Result.success(demandService.myList(getCurrentUserId(), pageNum, pageSize));
    }
}
