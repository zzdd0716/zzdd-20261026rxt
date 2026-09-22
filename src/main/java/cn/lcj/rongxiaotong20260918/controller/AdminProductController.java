package cn.lcj.rongxiaotong20260918.controller;

import cn.lcj.rongxiaotong20260918.common.PageResult;
import cn.lcj.rongxiaotong20260918.common.Result;
import cn.lcj.rongxiaotong20260918.pojo.dto.ProductAuditDTO;
import cn.lcj.rongxiaotong20260918.pojo.dto.ProductStatusDTO;
import cn.lcj.rongxiaotong20260918.pojo.vo.AdminProductListVO;
import cn.lcj.rongxiaotong20260918.service.AdminProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商品管理员模块（后端 B）
 * 接口文档：02-backend-B-trade-expert.md 第八章
 * 全部接口仅管理员（role=3）可调用。
 */
@RestController
@RequestMapping("/api/admin/products")
public class AdminProductController extends BaseController {

    @Autowired
    private AdminProductService adminProductService;

    /** 8.1 商品列表（管理员） */
    @GetMapping
    public Result<PageResult<AdminProductListVO>> pageList(
            @RequestParam(required = false) Integer auditStatus,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false, defaultValue = "1") Integer pageNum) {
        return Result.success(adminProductService.pageList(
                getCurrentUserId(), auditStatus, status, keyword, pageNum, 10));
    }

    /** 8.2 商品审核通过 */
    @PutMapping("/{id}/approve")
    public Result<Void> approve(@PathVariable Long id) {
        adminProductService.approve(getCurrentUserId(), id);
        return Result.success();
    }

    /** 8.3 商品审核驳回（驳回原因必填） */
    @PutMapping("/{id}/reject")
    public Result<Void> reject(@PathVariable Long id, @RequestBody ProductAuditDTO dto) {
        adminProductService.reject(getCurrentUserId(), id, dto == null ? null : dto.getAuditRemark());
        return Result.success();
    }

    /** 8.4 商品强制上下架 */
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestBody ProductStatusDTO dto) {
        adminProductService.updateStatus(getCurrentUserId(), id, dto == null ? null : dto.getStatus());
        return Result.success();
    }

    /** 8.5 删除商品（管理员） */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        adminProductService.delete(getCurrentUserId(), id);
        return Result.success();
    }
}
