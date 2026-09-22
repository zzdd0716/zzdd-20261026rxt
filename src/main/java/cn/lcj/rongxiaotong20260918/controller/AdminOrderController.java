package cn.lcj.rongxiaotong20260918.controller;

import cn.lcj.rongxiaotong20260918.common.PageResult;
import cn.lcj.rongxiaotong20260918.common.Result;
import cn.lcj.rongxiaotong20260918.pojo.vo.AdminOrderListVO;
import cn.lcj.rongxiaotong20260918.service.AdminOrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 订单管理员模块（后端 B）
 * 接口文档：02-backend-B-trade-expert.md 第十章
 * 仅管理员（role=3）可调用。
 */
@RestController
@RequestMapping("/api/admin/orders")
public class AdminOrderController extends BaseController {

    @Autowired
    private AdminOrderService adminOrderService;

    /** 10.1 订单列表（管理员） */
    @GetMapping
    public Result<PageResult<AdminOrderListVO>> pageList(
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false, defaultValue = "1") Integer pageNum) {
        return Result.success(adminOrderService.pageList(
                getCurrentUserId(), status, orderNo, pageNum, 10));
    }
}
