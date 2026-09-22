package cn.lcj.rongxiaotong20260918.controller;

import cn.lcj.rongxiaotong20260918.common.PageResult;
import cn.lcj.rongxiaotong20260918.common.Result;
import cn.lcj.rongxiaotong20260918.pojo.dto.ProductDTO;
import cn.lcj.rongxiaotong20260918.pojo.entity.Product;
import cn.lcj.rongxiaotong20260918.pojo.vo.ProductDetailVO;
import cn.lcj.rongxiaotong20260918.pojo.vo.ProductListVO;
import cn.lcj.rongxiaotong20260918.service.ProductService;
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
 * 商品/货源模块（后端 B）
 * 接口文档：02-backend-B-trade-expert.md 第一章
 *
 * 权限说明（开发期无 Spring Security，当前用户从请求头 X-User-Id 读取；
 * 生产期启用 JWT 后由 SecurityConfig 拦截，需登录接口自动生效）：
 * - 公开：列表、详情
 * - 需登录（农民/卖家）：发布、修改、上架下架、删除、我的货源
 */
@RestController
@RequestMapping("/api/product")
public class ProductController extends BaseController {

    @Autowired
    private ProductService productService;

    /** 1.1 商品列表（公开） （已经完成）*/
    @GetMapping
    public Result<PageResult<ProductListVO>> pageList(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String origin,
            @RequestParam(required = false, defaultValue = "1") Integer pageNum,
            @RequestParam(required = false, defaultValue = "10") Integer pageSize) {
        return Result.success(productService.pageList(keyword, categoryId, origin, pageNum, pageSize));
    }

    /** 1.2 商品详情（公开） （已经完成）*/
    @GetMapping("/{id}")
    public Result<ProductDetailVO> getDetail(@PathVariable Long id) {
        return Result.success(productService.getDetail(id));
    }

    /** 1.3 发布货源（需登录） */
    @PostMapping
    public Result<Map<String, Long>> create(@RequestBody ProductDTO dto) {
        Long id = productService.create(getCurrentUserId(), dto);
        return Result.success(Map.of("id", id));
    }

    /** 1.4 修改自己的货源（需登录） */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody ProductDTO dto) {
        productService.update(getCurrentUserId(), id, dto);
        return Result.success();
    }

    /** 1.5 上架/下架（需登录） */
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        productService.updateStatus(getCurrentUserId(), id, body.get("status"));
        return Result.success();
    }

    /** 1.6 删除自己的货源（需登录） */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        productService.delete(getCurrentUserId(), id);
        return Result.success();
    }

    /** 1.7 我的货源（需登录） （已经完成）*/
    @GetMapping("/my")
    public Result<PageResult<Product>> myList(
            @RequestParam(required = false) Integer auditStatus,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false, defaultValue = "1") Integer pageNum,
            @RequestParam(required = false, defaultValue = "10") Integer pageSize) {
        System.out.println(getCurrentUserId());
        return Result.success(productService.myList(getCurrentUserId(), auditStatus, status, pageNum, pageSize));
    }
}
