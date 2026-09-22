package cn.lcj.rongxiaotong20260918.pojo.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品列表（管理员视角）
 */
@Data
public class AdminProductListVO {

    private Long id;

    /** 商品名称 */
    private String name;

    /** 单价 */
    private BigDecimal price;

    /** 库存 */
    private Integer stock;

    /** 主图 */
    private String image;

    /** 0待审核/1通过/2驳回 */
    private Integer auditStatus;

    /** 驳回原因 */
    private String auditRemark;

    /** 0下架/1上架 */
    private Integer status;

    /** 卖家信息（id/realName） */
    private UserBriefVO seller;

    private LocalDateTime createTime;
}
