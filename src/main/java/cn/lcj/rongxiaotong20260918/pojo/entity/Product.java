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
@TableName("product")
public class Product {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long sellerId;
    private String name;
    private Long categoryId;
    private BigDecimal price;
    private String unit;
    private Integer stock;
    private String image;
    private String images;
    private String description;
    private String origin;
    private Integer auditStatus;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}