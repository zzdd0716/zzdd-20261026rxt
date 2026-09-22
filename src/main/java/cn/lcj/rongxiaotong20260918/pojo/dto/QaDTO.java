package cn.lcj.rongxiaotong20260918.pojo.dto;

import lombok.Data;

/**
 * 发起提问/修改问题 请求体（共用）
 */
@Data
public class QaDTO {

    /** 问题标题（必填） */
    private String title;

    /** 问题内容（必填） */
    private String content;

    /** 问题分类（必填） */
    private Long categoryId;

    /** 指定专家ID（user.id，不传=公开提问） */
    private Long expertId;

    /** 图片JSON字符串 */
    private String images;
}
