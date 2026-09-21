package cn.lcj.rongxiaotong20260918.pojo.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "product")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "category_id")
    private Long categoryId;

    @Column(name = "price", nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "unit", length = 20)
    private String unit;

    @Column(name = "stock")
    private Integer stock;

    @Column(name = "image", length = 255)
    private String image;

    @Column(name = "images", columnDefinition = "TEXT")
    private String images;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "origin", length = 100)
    private String origin;

    @Column(name = "audit_status")
    private Integer auditStatus;

    @Column(name = "status")
    private Integer status;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "update_time")
    private LocalDateTime updateTime;
}