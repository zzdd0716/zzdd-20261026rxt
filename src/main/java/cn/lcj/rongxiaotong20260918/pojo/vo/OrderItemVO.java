package cn.lcj.rongxiaotong20260918.pojo.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 订单明细项
 */
@Data
public class OrderItemVO {

    private Long id;

    private Long productId;

    /** 下单时商品名称快照 */
    private String productName;

    /** 下单时商品主图快照 */
    private String productImage;

    /** 下单时单价快照 */
    private BigDecimal price;

    private Integer quantity;

    /** 小计 = price * quantity */
    private BigDecimal subtotal;
}
