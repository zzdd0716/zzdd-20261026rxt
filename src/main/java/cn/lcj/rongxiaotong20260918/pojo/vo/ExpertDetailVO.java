package cn.lcj.rongxiaotong20260918.pojo.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 专家详情（专家ID = user.id，含未来14天忙碌时段）
 */
@Data
public class ExpertDetailVO {

    /** 专家ID（user.id） */
    private Long id;

    /** 关联用户ID */
    private Long userId;

    private String realName;

    private String avatar;

    private String phone;

    private String specialty;

    private String title;

    private String introduction;

    private BigDecimal consultPrice;

    private BigDecimal rating;

    private Integer consultCount;

    /** 未来14天每天被占用的时段 */
    private List<ExpertDateBusyVO> availableTimes;
}
