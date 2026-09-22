package cn.lcj.rongxiaotong20260918.pojo.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 商品详情（公开详情接口返回）
 */
@Data
public class ProductDetailVO {

    private Long id;

    /** 卖家ID */
    private Long sellerId;

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

    /** 多图数组（由 images JSON 字符串解析而来） */
    private List<String> images;

    /** 商品描述 */
    private String description;

    /** 产地 */
    private String origin;

    /** 审核状态：0-待审核 1-审核通过 2-已驳回 */
    private Integer auditStatus;

    /** 上下架状态：0-下架 1-上架 */
    private Integer status;

    /** 卖家完整信息（id / username / realName / farmName / phone） */
    private UserBriefVO seller;

    private LocalDateTime createTime;
}
