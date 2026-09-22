package cn.lcj.rongxiaotong20260918.pojo.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 需求列表（管理员视角）
 */
@Data
public class AdminDemandListVO {

    private Long id;

    /** 需求名称 */
    private String name;

    /** 需求数量 */
    private Integer quantity;

    /** 期望价格 */
    private BigDecimal expectedPrice;

    /** 0已关闭/1进行中/2已完成 */
    private Integer status;

    /** 发布人信息（id/realName） */
    private UserBriefVO buyer;

    private LocalDateTime createTime;
}
