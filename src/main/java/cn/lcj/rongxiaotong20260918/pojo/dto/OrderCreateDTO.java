package cn.lcj.rongxiaotong20260918.pojo.dto;

import lombok.Data;

import java.util.List;

/**
 * 创建订单请求体
 */
@Data
public class OrderCreateDTO {

    /** 收货地址ID（必须属于当前用户） */
    private Long addressId;

    /** 购物车项ID数组（已选中状态） */
    private List<Long> cartIds;

    /** 订单备注 */
    private String remark;
}
