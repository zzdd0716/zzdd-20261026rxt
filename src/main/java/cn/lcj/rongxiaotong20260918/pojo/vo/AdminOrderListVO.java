package cn.lcj.rongxiaotong20260918.pojo.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单列表（管理员视角）
 */
@Data
public class AdminOrderListVO {

    private Long id;

    /** 订单号 */
    private String orderNo;

    /** 订单金额 */
    private BigDecimal totalAmount;

    /** 0待支付/1已支付/2已发货/3已完成/4已取消 */
    private Integer status;

    /** 0无退款/1申请中/2已退款/3拒绝 */
    private Integer refundStatus;

    /** 买家信息 */
    private UserBriefVO buyer;

    /** 卖家信息 */
    private UserBriefVO seller;

    private LocalDateTime createTime;
}
