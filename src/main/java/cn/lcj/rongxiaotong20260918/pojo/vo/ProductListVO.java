package cn.lcj.rongxiaotong20260918.pojo.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品列表项（公开列表接口返回）
 */
@Data
public class ProductListVO {

    private Long id;

    /** 商品名称 */
    private String name;

    private Long categoryId;

    /** 分类名称 */
    private String categoryName;

    /** 单价 */
    private BigDecimal price;

    /** 单位 */
    private String unit;

    /** 库存 */
    private Integer stock;

    /** 主图 */
    private String image;

    /** 产地 */
    private String origin;

    private LocalDateTime createTime;

    /** 卖家信息（id / realName / farmName） */
    private UserBriefVO seller;
}
