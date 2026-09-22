package cn.lcj.rongxiaotong20260918.pojo.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 我的提问列表项
 */
@Data
public class QaMyQuestionVO {

    private Long id;

    private String title;

    /** 0待回答/1已回答/2已关闭 */
    private Integer status;

    private Integer replyCount;

    private LocalDateTime createTime;
}
