package cn.lcj.rongxiaotong20260918.pojo.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 我的回答列表项
 */
@Data
public class QaMyReplyVO {

    private Long id;

    private String content;

    /** 所属问题摘要 */
    private QaBriefVO qa;

    private LocalDateTime createTime;
}
