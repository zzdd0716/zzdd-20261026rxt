package cn.lcj.rongxiaotong20260918.pojo.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 需求列表项（公开列表接口返回）
 */
@Data
public class DemandListVO {

    private Long id;

    private Long buyerId;

    /** 需求商品名称 */
    private String name;

    /** 需求数量 */
    private Integer quantity;

    /** 单位 */
    private String unit;

    /** 期望单价 */
    private BigDecimal expectedPrice;

    /** 截止日期 */
    private LocalDateTime deadline;

    /** 状态：0-已关闭 1-进行中 2-已完成 */
    private Integer status;

    /** 买家信息（id / realName / farmName） */
    private UserBriefVO buyer;

    private LocalDateTime createTime;
}
