package cn.lcj.rongxiaotong20260918.service;

import cn.lcj.rongxiaotong20260918.common.PageResult;
import cn.lcj.rongxiaotong20260918.pojo.dto.OrderCreateDTO;
import cn.lcj.rongxiaotong20260918.pojo.vo.OrderCreateVO;
import cn.lcj.rongxiaotong20260918.pojo.vo.OrderDetailVO;
import cn.lcj.rongxiaotong20260918.pojo.vo.OrderListVO;

public interface OrdersService {

    /**
     * 创建订单（需登录，不同卖家自动拆单，扣库存、写明细、清购物车）
     */
    OrderCreateVO create(Long userId, OrderCreateDTO dto);

    /**
     * 我的采购订单（买家视角，需登录）
     */
    PageResult<OrderListVO> purchaseList(Long userId, Integer status, Integer pageNum, Integer pageSize);

    /**
     * 我的销售订单（卖家视角，需登录）
     */
    PageResult<OrderListVO> saleList(Long userId, Integer status, Integer pageNum, Integer pageSize);

    /**
     * 订单详情（需登录，买家/卖家/管理员可看）
     */
    OrderDetailVO detail(Long userId, Long id);

    /**
     * 模拟支付（买家，仅待支付）
     */
    void pay(Long userId, Long id);

    /**
     * 卖家发货（仅已支付）
     */
    void ship(Long userId, Long id);

    /**
     * 买家确认收货（仅已发货）
     */
    void finish(Long userId, Long id);

    /**
     * 取消订单（买家，仅待支付，恢复库存）
     */
    void cancel(Long userId, Long id);

    /**
     * 申请退款（买家，已支付后可申请）
     */
    void refund(Long userId, Long id, String refundRemark);
}
