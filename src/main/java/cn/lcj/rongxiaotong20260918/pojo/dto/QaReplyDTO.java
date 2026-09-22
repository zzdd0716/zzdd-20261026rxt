package cn.lcj.rongxiaotong20260918.pojo.dto;

import lombok.Data;

/**
 * 回答问题请求体
 */
@Data
public class QaReplyDTO {

    /** 回答内容（必填） */
    private String content;
}
