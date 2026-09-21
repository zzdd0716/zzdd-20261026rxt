package cn.lcj.rongxiaotong20260918.pojo.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "address")
public class Address {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "receiver", nullable = false, length = 50)
    private String receiver;

    @Column(name = "phone", nullable = false, length = 20)
    private String phone;

    @Column(name = "province", length = 20)
    private String province;

    @Column(name = "city", length = 20)
    private String city;

    @Column(name = "district", length = 20)
    private String district;

    @Column(name = "detail", length = 255)
    private String detail;

    @Column(name = "is_default")
    private Integer isDefault;

    @Column(name = "create_time")
    private LocalDateTime createTime;
}