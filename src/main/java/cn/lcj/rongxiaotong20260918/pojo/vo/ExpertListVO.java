package cn.lcj.rongxiaotong20260918.pojo.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 专家列表项（专家ID = user.id，与预约表 appointment.expert_id 一致）
 */
@Data
public class ExpertListVO {

    /** 专家ID（user.id） */
    private Long id;

    /** 关联用户ID */
    private Long userId;

    private String realName;

    private String specialty;

    private String title;

    private BigDecimal rating;

    private Integer consultCount;

    private BigDecimal consultPrice;

    private String avatar;
}
