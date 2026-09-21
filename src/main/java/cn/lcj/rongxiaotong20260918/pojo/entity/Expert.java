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
@TableName("expert")
public class Expert {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String specialty;
    private String title;
    private String introduction;
    private BigDecimal consultPrice;
    private BigDecimal rating;
    private Integer consultCount;
    private String certification;
    private Integer auditStatus;
    private String auditRemark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}