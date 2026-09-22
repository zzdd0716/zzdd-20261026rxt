package cn.lcj.rongxiaotong20260918.controller;

import cn.lcj.rongxiaotong20260918.common.PageResult;
import cn.lcj.rongxiaotong20260918.common.Result;
import cn.lcj.rongxiaotong20260918.pojo.dto.OrderCreateDTO;
import cn.lcj.rongxiaotong20260918.pojo.dto.OrderRefundDTO;
import cn.lcj.rongxiaotong20260918.pojo.vo.OrderCreateVO;
import cn.lcj.rongxiaotong20260918.pojo.vo.OrderDetailVO;
import cn.lcj.rongxiaotong20260918.pojo.vo.OrderListVO;
import cn.lcj.rongxiaotong20260918.service.OrdersService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 订单模块（后端 B）
 * 接口文档：02-backend-B-trade-expert.md 第四章
 *
 * 权限说明（开发期无 Spring Security，当前用户从请求头 X-User-Id 读取；
 * 生产期启用 JWT 后由 SecurityConfig 拦截，需登录接口自动生效）：
 * - 全部接口需登录
 */
@RestController
@RequestMapping("/api/order")
public class OrdersController extends BaseController {

    @Autowired
    private OrdersService ordersService;

    /** 4.1 创建订单（需登录，按卖家自动拆单） */
    @PostMapping
    public Result<OrderCreateVO> create(@RequestBody OrderCreateDTO dto) {
        return Result.success(ordersService.create(getCurrentUserId(), dto));
    }

    /** 4.2 我的采购订单（买家视角） */
    @GetMapping("/purchase")
    public Result<PageResult<OrderListVO>> purchaseList(
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false, defaultValue = "1") Integer pageNum,
            @RequestParam(required = false, defaultValue = "10") Integer pageSize) {
        return Result.success(ordersService.purchaseList(getCurrentUserId(), status, pageNum, pageSize));
    }

    /** 4.3 我的销售订单（卖家视角） */
    @GetMapping("/sale")
    public Result<PageResult<OrderListVO>> saleList(
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false, defaultValue = "1") Integer pageNum,
            @RequestParam(required = false, defaultValue = "10") Integer pageSize) {
        return Result.success(ordersService.saleList(getCurrentUserId(), status, pageNum, pageSize));
    }

    /** 4.4 订单详情（买家/卖家/管理员） */
    @GetMapping("/{id}")
    public Result<OrderDetailVO> detail(@PathVariable Long id) {
        return Result.success(ordersService.detail(getCurrentUserId(), id));
    }

    /** 4.5 模拟支付（买家） */
    @PutMapping("/{id}/pay")
    public Result<Void> pay(@PathVariable Long id) {
        ordersService.pay(getCurrentUserId(), id);
        return Result.success();
    }

    /** 4.6 卖家发货 */
    @PutMapping("/{id}/ship")
    public Result<Void> ship(@PathVariable Long id) {
        ordersService.ship(getCurrentUserId(), id);
        return Result.success();
    }

    /** 4.7 买家确认收货 */
    @PutMapping("/{id}/finish")
    public Result<Void> finish(@PathVariable Long id) {
        ordersService.finish(getCurrentUserId(), id);
        return Result.success();
    }

    /** 4.8 取消订单（买家） */
    @PutMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id) {
        ordersService.cancel(getCurrentUserId(), id);
        return Result.success();
    }

    /** 4.9 申请退款（买家） */
    @PostMapping("/{id}/refund")
    public Result<Void> refund(@PathVariable Long id, @RequestBody OrderRefundDTO dto) {
        ordersService.refund(getCurrentUserId(), id, dto.getRefundRemark());
        return Result.success();
    }
}
