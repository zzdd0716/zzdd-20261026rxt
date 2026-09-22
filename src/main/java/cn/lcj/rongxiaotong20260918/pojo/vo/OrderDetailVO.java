package cn.lcj.rongxiaotong20260918.pojo.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单详情
 */
@Data
public class OrderDetailVO {

    private Long id;

    private String orderNo;

    private Long buyerId;

    private Long sellerId;

    private String remark;

    private BigDecimal totalAmount;

    /** 0待支付/1已支付/2已发货/3已完成/4已取消 */
    private Integer status;

    /** 0无退款/1退款申请中/2已退款/3退款拒绝 */
    private Integer refundStatus;

    private String refundRemark;

    private String receiver;

    private String receiverPhone;

    private String receiverAddress;

    private LocalDateTime payTime;

    private LocalDateTime shipTime;

    private LocalDateTime finishTime;

    private LocalDateTime createTime;

    /** 订单明细 */
    private List<OrderItemVO> items;
}
