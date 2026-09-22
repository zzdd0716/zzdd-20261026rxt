package cn.lcj.rongxiaotong20260918.pojo.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 问答详情
 */
@Data
public class QaDetailVO {

    private Long id;

    private String title;

    private String content;

    private String images;

    private Long categoryId;

    /** 0待回答/1已回答/2已关闭 */
    private Integer status;

    private Integer viewCount;

    private Integer replyCount;

    private UserBriefVO questioner;

    /** 指定专家（公开提问为 null） */
    private UserBriefVO expert;

    private LocalDateTime createTime;

    /** 全部回答 */
    private List<QaReplyVO> replies;
}
