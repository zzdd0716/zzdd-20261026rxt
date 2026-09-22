package cn.lcj.rongxiaotong20260918.pojo.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单列表项（采购列表填 seller，销售列表填 buyer）
 * 空字段不参与序列化，保证与接口文档字段一致
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OrderListVO {

    private Long id;

    private String orderNo;

    private BigDecimal totalAmount;

    /** 0待支付/1已支付/2已发货/3已完成/4已取消 */
    private Integer status;

    /** 0无退款/1退款申请中/2已退款/3退款拒绝 */
    private Integer refundStatus;

    private String receiver;

    private String receiverPhone;

    private String receiverAddress;

    /** 卖家信息（采购列表返回） */
    private UserBriefVO seller;

    /** 买家信息（销售列表返回） */
    private UserBriefVO buyer;

    private LocalDateTime createTime;
}
