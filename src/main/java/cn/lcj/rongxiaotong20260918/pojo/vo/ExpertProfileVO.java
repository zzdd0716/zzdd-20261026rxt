package cn.lcj.rongxiaotong20260918.pojo.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 专家自己的资料（profile）
 */
@Data
public class ExpertProfileVO {

    private String specialty;

    private String title;

    private String introduction;

    private BigDecimal consultPrice;

    private BigDecimal rating;

    private Integer consultCount;

    /** 0-待审核 1-审核通过 2-已驳回 */
    private Integer auditStatus;

    private String auditRemark;
}
