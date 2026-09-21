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
@Table(name = "`orders`")
public class Orders {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_no", nullable = false, unique = true, length = 50)
    private String orderNo;

    @Column(name = "buyer_id", nullable = false)
    private Long buyerId;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "address_id")
    private Long addressId;

    @Column(name = "receiver", length = 50)
    private String receiver;

    @Column(name = "receiver_phone", length = 20)
    private String receiverPhone;

    @Column(name = "receiver_address", length = 255)
    private String receiverAddress;

    @Column(name = "remark", length = 255)
    private String remark;

    @Column(name = "status")
    private Integer status;

    @Column(name = "refund_status")
    private Integer refundStatus;

    @Column(name = "refund_remark", length = 255)
    private String refundRemark;

    @Column(name = "pay_time")
    private LocalDateTime payTime;

    @Column(name = "ship_time")
    private LocalDateTime shipTime;

    @Column(name = "finish_time")
    private LocalDateTime finishTime;

    @Column(name = "create_time")
    private LocalDateTime createTime;
}