package cn.lcj.rongxiaotong20260918.pojo.dto;

import lombok.Data;

/**
 * 申请退款请求体
 */
@Data
public class OrderRefundDTO {

    /** 退款原因 */
    private String refundRemark;
}
