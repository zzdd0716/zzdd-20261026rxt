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
@TableName("finance_product")
public class FinanceProduct {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String bankName;
    private String name;
    private String purpose;
    private BigDecimal minAmount;
    private BigDecimal maxAmount;
    private Integer minPeriod;
    private Integer maxPeriod;
    private BigDecimal interestRate;
    private String description;
    private Integer status;
    private LocalDateTime createTime;
}