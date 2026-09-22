package cn.lcj.rongxiaotong20260918.pojo.dto;

import lombok.Data;

/**
 * 加入购物车请求体
 */
@Data
public class CartAddDTO {

    /** 商品ID（必须上架且审核通过） */
    private Long productId;

    /** 数量，>0 且不超库存 */
    private Integer quantity;
}
