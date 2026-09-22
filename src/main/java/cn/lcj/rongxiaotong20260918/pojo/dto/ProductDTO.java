package cn.lcj.rongxiaotong20260918.pojo.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 商品/货源 发布、修改 请求体
 * 说明：发布时必填字段在 Service 层校验；修改时"只传要修改的字段"，空字段不更新。
 */
@Data
public class ProductDTO {

    /** 商品名称 */
    private String name;

    /** 分类ID（商品分类，type=1） */
    private Long categoryId;

    /** 单价 */
    private BigDecimal price;

    /** 单位，默认"斤" */
    private String unit;

    /** 库存数量 */
    private Integer stock;

    /** 主图URL */
    private String image;

    /** 多图JSON字符串，如 ["/images/a.jpg","/images/b.jpg"] */
    private String images;

    /** 商品描述 */
    private String description;

    /** 产地 */
    private String origin;
}
