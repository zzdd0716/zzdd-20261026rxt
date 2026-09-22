package cn.lcj.rongxiaotong20260918.pojo.vo;

import lombok.Data;

/**
 * 预约列表中的专家摘要（id=user.id）
 */
@Data
public class AppointmentExpertVO {

    private Long id;

    private String realName;

    private String specialty;
}
