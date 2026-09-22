package cn.lcj.rongxiaotong20260918.controller;

import cn.lcj.rongxiaotong20260918.common.PageResult;
import cn.lcj.rongxiaotong20260918.common.Result;
import cn.lcj.rongxiaotong20260918.pojo.vo.AdminDemandListVO;
import cn.lcj.rongxiaotong20260918.service.AdminDemandService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 需求管理员模块（后端 B）
 * 接口文档：02-backend-B-trade-expert.md 第九章
 * 全部接口仅管理员（role=3）可调用。
 */
@RestController
@RequestMapping("/api/admin/demands")
public class AdminDemandController extends BaseController {

    @Autowired
    private AdminDemandService adminDemandService;

    /** 9.1 需求列表（管理员） */
    @GetMapping
    public Result<PageResult<AdminDemandListVO>> pageList(
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false, defaultValue = "1") Integer pageNum) {
        return Result.success(adminDemandService.pageList(
                getCurrentUserId(), status, keyword, pageNum, 10));
    }

    /** 9.2 删除需求（管理员） */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        adminDemandService.delete(getCurrentUserId(), id);
        return Result.success();
    }
}
