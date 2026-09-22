package cn.lcj.rongxiaotong20260918.pojo.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 创建订单响应（不同卖家自动拆成多单）
 */
@Data
public class OrderCreateVO {

    /** 拆单后的订单号列表 */
    private List<String> orderNos;

    /** 总金额 */
    private BigDecimal totalAmount;
}
