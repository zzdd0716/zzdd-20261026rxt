package cn.lcj.rongxiaotong20260918.pojo.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 问答回复项
 */
@Data
public class QaReplyVO {

    private Long id;

    private String content;

    /** 0-普通用户 1-专家回答 */
    private Integer isExpert;

    /** 回答者 */
    private UserBriefVO replier;

    private LocalDateTime createTime;
}
