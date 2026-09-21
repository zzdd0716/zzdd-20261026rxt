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
@TableName("demand")
public class Demand {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long buyerId;
    private String name;
    private Long categoryId;
    private Integer quantity;
    private String unit;
    private BigDecimal expectedPrice;
    private String description;
    private LocalDateTime deadline;
    private Integer status;
    private LocalDateTime createTime;
}