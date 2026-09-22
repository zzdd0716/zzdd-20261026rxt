package cn.lcj.rongxiaotong20260918.controller;

import cn.lcj.rongxiaotong20260918.common.Result;
import cn.lcj.rongxiaotong20260918.pojo.dto.CartAddDTO;
import cn.lcj.rongxiaotong20260918.pojo.dto.CartQuantityDTO;
import cn.lcj.rongxiaotong20260918.pojo.dto.CartSelectDTO;
import cn.lcj.rongxiaotong20260918.pojo.vo.CartItemVO;
import cn.lcj.rongxiaotong20260918.service.CartService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 购物车模块（后端 B）
 * 接口文档：02-backend-B-trade-expert.md 第三章
 *
 * 权限说明（开发期无 Spring Security，当前用户从请求头 X-User-Id 读取；
 * 生产期启用 JWT 后由 SecurityConfig 拦截，需登录接口自动生效）：
 * - 全部接口需登录
 */
@RestController
@RequestMapping("/api/cart")
public class CartController extends BaseController {

    @Autowired
    private CartService cartService;

    /** 3.1 购物车列表（需登录） */
    @GetMapping
    public Result<List<CartItemVO>> list() {
        return Result.success(cartService.list(getCurrentUserId()));
    }

    /** 3.2 加入购物车（需登录） */
    @PostMapping
    public Result<Void> add(@RequestBody CartAddDTO dto) {
        cartService.add(getCurrentUserId(), dto);
        return Result.success();
    }

    /** 3.3 修改数量（需登录） */
    @PutMapping("/{id}")
    public Result<Void> updateQuantity(@PathVariable Long id, @RequestBody CartQuantityDTO dto) {
        cartService.updateQuantity(getCurrentUserId(), id, dto.getQuantity());
        return Result.success();
    }

    /** 3.4 删除购物车项（需登录） */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        cartService.delete(getCurrentUserId(), id);
        return Result.success();
    }

    /** 3.5 批量勾选（需登录） */
    @PutMapping("/selected")
    public Result<Void> selectBatch(@RequestBody CartSelectDTO dto) {
        cartService.selectBatch(getCurrentUserId(), dto);
        return Result.success();
    }
}
