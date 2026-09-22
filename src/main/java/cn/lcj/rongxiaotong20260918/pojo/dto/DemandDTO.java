package cn.lcj.rongxiaotong20260918.pojo.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 采购需求 发布、修改 请求体
 * 说明：发布时必填字段在 Service 层校验；修改时"只传要修改的字段"，空字段不更新。
 */
@Data
public class DemandDTO {

    /** 需求商品名称 */
    private String name;

    /** 分类ID（商品分类，type=1） */
    private Long categoryId;

    /** 需求数量 */
    private Integer quantity;

    /** 单位，默认"斤" */
    private String unit;

    /** 期望单价 */
    private BigDecimal expectedPrice;

    /** 详细要求 */
    private String description;

    /** 截止日期，格式 "2026-10-01 12:00:00" */
    private LocalDateTime deadline;
}
