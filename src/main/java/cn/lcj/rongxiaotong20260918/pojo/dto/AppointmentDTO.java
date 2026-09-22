package cn.lcj.rongxiaotong20260918.pojo.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 创建预约请求体
 */
@Data
public class AppointmentDTO {

    /** 专家ID（user.id，专家审核通过） */
    private Long expertId;

    /** 预约时间，格式 yyyy-MM-dd HH:mm:ss */
    private LocalDateTime appointmentTime;

    /** 时长（分钟，默认 30） */
    private Integer duration;

    /** 咨询主题 */
    private String topic;
}
