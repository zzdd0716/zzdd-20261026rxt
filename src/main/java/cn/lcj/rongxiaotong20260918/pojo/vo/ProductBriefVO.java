package cn.lcj.rongxiaotong20260918.pojo.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 购物车项内嵌的商品简要信息
 */
@Data
public class ProductBriefVO {

    /** 商品名称 */
    private String name;

    /** 单价 */
    private BigDecimal price;

    /** 主图 */
    private String image;

    /** 库存 */
    private Integer stock;
}
