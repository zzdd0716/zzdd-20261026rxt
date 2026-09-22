package cn.lcj.rongxiaotong20260918.pojo.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 专家完善资料请求体
 */
@Data
public class ExpertProfileDTO {

    /** 专长领域（必填） */
    private String specialty;

    /** 职称 */
    private String title;

    /** 个人简介 */
    private String introduction;

    /** 咨询价格（元/次，必填，未传默认 0） */
    private BigDecimal consultPrice;
}
