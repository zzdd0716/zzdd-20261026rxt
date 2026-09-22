package cn.lcj.rongxiaotong20260918.service;

import cn.lcj.rongxiaotong20260918.common.PageResult;
import cn.lcj.rongxiaotong20260918.pojo.dto.QaDTO;
import cn.lcj.rongxiaotong20260918.pojo.dto.QaReplyDTO;
import cn.lcj.rongxiaotong20260918.pojo.vo.QaCreateVO;
import cn.lcj.rongxiaotong20260918.pojo.vo.QaDetailVO;
import cn.lcj.rongxiaotong20260918.pojo.vo.QaListVO;
import cn.lcj.rongxiaotong20260918.pojo.vo.QaMyQuestionVO;
import cn.lcj.rongxiaotong20260918.pojo.vo.QaMyReplyVO;

import java.util.List;

public interface QaService {

    /**
     * 问答列表（公开）
     */
    PageResult<QaListVO> list(String keyword, Long categoryId, Integer status, Integer pageNum, Integer pageSize);

    /**
     * 问答详情（公开，浏览数 +1）
     */
    QaDetailVO detail(Long id);

    /**
     * 发起提问（需登录）
     */
    QaCreateVO create(Long userId, QaDTO dto);

    /**
     * 修改自己的问题（校验提问者）
     */
    void update(Long userId, Long id, QaDTO dto);

    /**
     * 删除自己的问题（校验提问者，同时删除其回答）
     */
    void delete(Long userId, Long id);

    /**
     * 回答问题（需登录，回答后问题转为已回答）
     */
    void reply(Long userId, Long id, QaReplyDTO dto);

    /**
     * 我的提问（需登录）
     */
    List<QaMyQuestionVO> myQuestion(Long userId);

    /**
     * 我的回答（需登录）
     */
    List<QaMyReplyVO> myReply(Long userId);

    /**
     * 删除自己的回答（校验回答者，同步减少回复数）
     */
    void deleteReply(Long userId, Long replyId);
}
