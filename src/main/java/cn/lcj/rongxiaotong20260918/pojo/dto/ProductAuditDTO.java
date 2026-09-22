package cn.lcj.rongxiaotong20260918.pojo.dto;

import lombok.Data;

/**
 * 商品审核驳回请求体
 */
@Data
public class ProductAuditDTO {

    /** 驳回原因（必填） */
    private String auditRemark;
}
