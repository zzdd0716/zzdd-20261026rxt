package cn.lcj.rongxiaotong20260918.pojo.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("appointment")
public class Appointment {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long expertId;
    private LocalDateTime appointmentTime;
    private Integer duration;
    private String topic;
    private Integer status;
    private BigDecimal amount;
    private String remark;
    private LocalDateTime createTime;
}