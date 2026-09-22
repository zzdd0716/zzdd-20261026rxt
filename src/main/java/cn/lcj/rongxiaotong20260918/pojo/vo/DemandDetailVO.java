package cn.lcj.rongxiaotong20260918.pojo.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 需求详情（公开详情接口返回）
 */
@Data
public class DemandDetailVO {

    private Long id;

    private Long buyerId;

    /** 需求商品名称 */
    private String name;

    /** 详细要求 */
    private String description;

    /** 需求数量 */
    private Integer quantity;

    /** 单位 */
    private String unit;

    /** 期望单价 */
    private BigDecimal expectedPrice;

    private Long categoryId;

    /** 截止日期 */
    private LocalDateTime deadline;

    /** 状态：0-已关闭 1-进行中 2-已完成 */
    private Integer status;

    /** 买家完整信息（id / realName / farmName / phone） */
    private UserBriefVO buyer;

    private LocalDateTime createTime;
}
