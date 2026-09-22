package cn.lcj.rongxiaotong20260918.pojo.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 购物车列表项（购物车列表接口返回）
 */
@Data
public class CartItemVO {

    /** 购物车项ID */
    private Long id;

    private Long productId;

    /** 数量 */
    private Integer quantity;

    /** 0-未选中 1-选中 */
    private Integer selected;

    private LocalDateTime createTime;

    /** 商品简要信息（name / price / image / stock） */
    private ProductBriefVO product;
}
