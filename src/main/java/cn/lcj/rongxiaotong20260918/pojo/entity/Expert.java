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
@Table(name = "expert")
public class Expert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(name = "specialty", length = 100)
    private String specialty;

    @Column(name = "title", length = 50)
    private String title;

    @Column(name = "introduction", columnDefinition = "TEXT")
    private String introduction;

    @Column(name = "consult_price", precision = 10, scale = 2)
    private BigDecimal consultPrice;

    @Column(name = "rating", precision = 3, scale = 2)
    private BigDecimal rating;

    @Column(name = "consult_count")
    private Integer consultCount;

    @Column(name = "certification", length = 255)
    private String certification;

    @Column(name = "audit_status")
    private Integer auditStatus;

    @Column(name = "audit_remark", length = 255)
    private String auditRemark;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "update_time")
    private LocalDateTime updateTime;
}