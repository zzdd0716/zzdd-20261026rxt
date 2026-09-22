package cn.lcj.rongxiaotong20260918.pojo.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 预约列表项（用户端/专家端共用）
 */
@Data
public class AppointmentVO {

    private Long id;

    /** 咨询主题 */
    private String topic;

    private LocalDateTime appointmentTime;

    /** 时长（分钟） */
    private Integer duration;

    /** 预约金额（下单时锁定专家咨询价） */
    private java.math.BigDecimal amount;

    /** 0待确认/1已确认/2已完成/3已取消 */
    private Integer status;

    /** 专家信息（id=user.id） */
    private AppointmentExpertVO expert;

    private LocalDateTime createTime;
}
