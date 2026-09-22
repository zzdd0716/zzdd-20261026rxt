package cn.lcj.rongxiaotong20260918.pojo.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 问答列表项
 */
@Data
public class QaListVO {

    private Long id;

    private String title;

    private Long categoryId;

    /** 0待回答/1已回答/2已关闭 */
    private Integer status;

    private Integer viewCount;

    private Integer replyCount;

    /** 提问者 */
    private UserBriefVO questioner;

    /** 指定专家（公开提问为 null） */
    private UserBriefVO expert;

    private LocalDateTime createTime;
}
