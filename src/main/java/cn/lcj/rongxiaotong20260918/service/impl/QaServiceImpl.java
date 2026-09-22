package cn.lcj.rongxiaotong20260918.service.impl;

import cn.lcj.rongxiaotong20260918.common.BusinessException;
import cn.lcj.rongxiaotong20260918.common.PageResult;
import cn.lcj.rongxiaotong20260918.mapper.ExpertMapper;
import cn.lcj.rongxiaotong20260918.mapper.QaMapper;
import cn.lcj.rongxiaotong20260918.mapper.QaReplyMapper;
import cn.lcj.rongxiaotong20260918.mapper.UserMapper;
import cn.lcj.rongxiaotong20260918.pojo.dto.QaDTO;
import cn.lcj.rongxiaotong20260918.pojo.dto.QaReplyDTO;
import cn.lcj.rongxiaotong20260918.pojo.entity.Expert;
import cn.lcj.rongxiaotong20260918.pojo.entity.Qa;
import cn.lcj.rongxiaotong20260918.pojo.entity.QaReply;
import cn.lcj.rongxiaotong20260918.pojo.entity.User;
import cn.lcj.rongxiaotong20260918.pojo.vo.QaBriefVO;
import cn.lcj.rongxiaotong20260918.pojo.vo.QaCreateVO;
import cn.lcj.rongxiaotong20260918.pojo.vo.QaDetailVO;
import cn.lcj.rongxiaotong20260918.pojo.vo.QaListVO;
import cn.lcj.rongxiaotong20260918.pojo.vo.QaMyQuestionVO;
import cn.lcj.rongxiaotong20260918.pojo.vo.QaMyReplyVO;
import cn.lcj.rongxiaotong20260918.pojo.vo.QaReplyVO;
import cn.lcj.rongxiaotong20260918.pojo.vo.UserBriefVO;
import cn.lcj.rongxiaotong20260918.service.QaService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 在线问答 模块实现
 * 状态机：0待回答 -> 1已回答（有人回答）；2已关闭（预留）
 */
@Service
public class QaServiceImpl implements QaService {

    /** 问题状态 */
    private static final int STATUS_WAITING = 0;  // 待回答
    private static final int STATUS_ANSWERED = 1; // 已回答

    @Autowired
    private QaMapper qaMapper;
    @Autowired
    private QaReplyMapper qaReplyMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private ExpertMapper expertMapper;

    @Override
    public PageResult<QaListVO> list(String keyword, Long categoryId, Integer status, Integer pageNum, Integer pageSize) {
        int num = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int size = pageSize == null || pageSize < 1 ? 10 : pageSize;

        LambdaQueryWrapper<Qa> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(Qa::getTitle, kw).or().like(Qa::getContent, kw));
        }
        wrapper.eq(categoryId != null, Qa::getCategoryId, categoryId)
                .eq(status != null, Qa::getStatus, status)
                .orderByDesc(Qa::getCreateTime);
        Page<Qa> page = qaMapper.selectPage(new Page<>(num, size), wrapper);

        // 联查提问者 + 指定专家（均为 user 表）
        Set<Long> userIds = page.getRecords().stream()
                .flatMap(q -> java.util.stream.Stream.of(q.getQuestionerId(), q.getExpertId()))
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, User> userMap = userMap(userIds);

        List<QaListVO> list = page.getRecords().stream()
                .map(q -> toListVO(q, userMap))
                .collect(Collectors.toList());
        return PageResult.of(page, list);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public QaDetailVO detail(Long id) {
        Qa qa = mustGet(id);
        // 浏览数 +1
        Qa update = new Qa();
        update.setId(id);
        update.setViewCount((qa.getViewCount() == null ? 0 : qa.getViewCount()) + 1);
        qaMapper.updateById(update);
        qa.setViewCount(qa.getViewCount() + 1);

        Map<Long, User> userMap = userMap(
                java.util.stream.Stream.of(qa.getQuestionerId(), qa.getExpertId())
                        .filter(Objects::nonNull).collect(Collectors.toSet()));

        List<QaReply> replies = qaReplyMapper.selectList(
                new LambdaQueryWrapper<QaReply>().eq(QaReply::getQaId, id).orderByAsc(QaReply::getCreateTime));
        Map<Long, User> replierMap = userMap(
                replies.stream().map(QaReply::getReplierId).collect(Collectors.toSet()));

        QaDetailVO vo = new QaDetailVO();
        vo.setId(qa.getId());
        vo.setTitle(qa.getTitle());
        vo.setContent(qa.getContent());
        vo.setImages(qa.getImages());
        vo.setCategoryId(qa.getCategoryId());
        vo.setStatus(qa.getStatus());
        vo.setViewCount(qa.getViewCount());
        vo.setReplyCount(qa.getReplyCount());
        vo.setQuestioner(toBrief(userMap.get(qa.getQuestionerId())));
        vo.setExpert(qa.getExpertId() == null ? null : toBrief(userMap.get(qa.getExpertId())));
        vo.setCreateTime(qa.getCreateTime());
        vo.setReplies(replies.stream()
                .map(r -> toReplyVO(r, replierMap.get(r.getReplierId())))
                .collect(Collectors.toList()));
        return vo;
    }

    @Override
    public QaCreateVO create(Long userId, QaDTO dto) {
        requireLogin(userId);
        validateQaDTO(dto);
        // 指定专家必须存在且为专家
        if (dto.getExpertId() != null && !isExpert(dto.getExpertId())) {
            throw new BusinessException(2002, "专家不存在");
        }
        Qa qa = new Qa();
        qa.setQuestionerId(userId);
        qa.setExpertId(dto.getExpertId());
        qa.setCategoryId(dto.getCategoryId());
        qa.setTitle(dto.getTitle().trim());
        qa.setContent(dto.getContent().trim());
        qa.setImages(dto.getImages());
        qa.setStatus(STATUS_WAITING);
        qa.setViewCount(0);
        qa.setReplyCount(0);
        qaMapper.insert(qa);

        QaCreateVO vo = new QaCreateVO();
        vo.setId(qa.getId());
        return vo;
    }

    @Override
    public void update(Long userId, Long id, QaDTO dto) {
        requireLogin(userId);
        validateQaDTO(dto);
        Qa qa = mustGet(id);
        checkQuestioner(qa, userId);
        if (dto.getExpertId() != null && !isExpert(dto.getExpertId())) {
            throw new BusinessException(2002, "专家不存在");
        }
        Qa update = new Qa();
        update.setId(id);
        update.setTitle(dto.getTitle().trim());
        update.setContent(dto.getContent().trim());
        update.setCategoryId(dto.getCategoryId());
        update.setExpertId(dto.getExpertId());
        update.setImages(dto.getImages());
        qaMapper.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long userId, Long id) {
        requireLogin(userId);
        Qa qa = mustGet(id);
        checkQuestioner(qa, userId);
        // 删除问题及其全部回答
        qaReplyMapper.delete(new LambdaQueryWrapper<QaReply>().eq(QaReply::getQaId, id));
        qaMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reply(Long userId, Long id, QaReplyDTO dto) {
        requireLogin(userId);
        if (dto == null || !StringUtils.hasText(dto.getContent())) {
            throw new BusinessException(2001, "回答内容不能为空");
        }
        Qa qa = mustGet(id);

        QaReply reply = new QaReply();
        reply.setQaId(id);
        reply.setReplierId(userId);
        reply.setContent(dto.getContent().trim());
        reply.setIsExpert(isExpert(userId) ? 1 : 0);
        qaReplyMapper.insert(reply);

        // 回复数 +1；问题从"待回答"转为"已回答"
        Qa update = new Qa();
        update.setId(id);
        update.setReplyCount((qa.getReplyCount() == null ? 0 : qa.getReplyCount()) + 1);
        if (qa.getStatus() != null && qa.getStatus() == STATUS_WAITING) {
            update.setStatus(STATUS_ANSWERED);
        }
        qaMapper.updateById(update);
    }

    @Override
    public List<QaMyQuestionVO> myQuestion(Long userId) {
        requireLogin(userId);
        List<Qa> list = qaMapper.selectList(
                new LambdaQueryWrapper<Qa>()
                        .eq(Qa::getQuestionerId, userId)
                        .orderByDesc(Qa::getCreateTime));
        return list.stream().map(q -> {
            QaMyQuestionVO vo = new QaMyQuestionVO();
            vo.setId(q.getId());
            vo.setTitle(q.getTitle());
            vo.setStatus(q.getStatus());
            vo.setReplyCount(q.getReplyCount());
            vo.setCreateTime(q.getCreateTime());
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    public List<QaMyReplyVO> myReply(Long userId) {
        requireLogin(userId);
        List<QaReply> list = qaReplyMapper.selectList(
                new LambdaQueryWrapper<QaReply>()
                        .eq(QaReply::getReplierId, userId)
                        .orderByDesc(QaReply::getCreateTime));
        Set<Long> qaIds = list.stream().map(QaReply::getQaId).collect(Collectors.toSet());
        Map<Long, Qa> qaMap = qaIds.isEmpty() ? Collections.emptyMap()
                : qaMapper.selectBatchIds(qaIds).stream()
                        .collect(Collectors.toMap(Qa::getId, Function.identity()));
        return list.stream().map(r -> {
            QaMyReplyVO vo = new QaMyReplyVO();
            vo.setId(r.getId());
            vo.setContent(r.getContent());
            vo.setCreateTime(r.getCreateTime());
            Qa qa = qaMap.get(r.getQaId());
            if (qa != null) {
                QaBriefVO brief = new QaBriefVO();
                brief.setId(qa.getId());
                brief.setTitle(qa.getTitle());
                vo.setQa(brief);
            }
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteReply(Long userId, Long replyId) {
        requireLogin(userId);
        QaReply reply = qaReplyMapper.selectById(replyId);
        if (reply == null) {
            throw new BusinessException(2002, "回答不存在");
        }
        if (!reply.getReplierId().equals(userId)) {
            throw new BusinessException(2003, "只能删除自己的回答");
        }
        qaReplyMapper.deleteById(replyId);
        // 回复数 -1；若回到 0 则问题恢复"待回答"
        Qa qa = qaMapper.selectById(reply.getQaId());
        if (qa != null) {
            int count = Math.max(0, (qa.getReplyCount() == null ? 0 : qa.getReplyCount()) - 1);
            Qa update = new Qa();
            update.setId(qa.getId());
            update.setReplyCount(count);
            if (count == 0 && qa.getStatus() != null && qa.getStatus() != STATUS_WAITING) {
                update.setStatus(STATUS_WAITING);
            }
            qaMapper.updateById(update);
        }
    }

    // ---------- 私有辅助方法 ----------

    private void requireLogin(Long userId) {
        if (userId == null) {
            throw new BusinessException(2001, "未登录或登录已过期");
        }
    }

    private void validateQaDTO(QaDTO dto) {
        if (dto == null) {
            throw new BusinessException(2001, "请求体不能为空");
        }
        if (!StringUtils.hasText(dto.getTitle())) {
            throw new BusinessException(2001, "问题标题不能为空");
        }
        if (!StringUtils.hasText(dto.getContent())) {
            throw new BusinessException(2001, "问题内容不能为空");
        }
        if (dto.getCategoryId() == null) {
            throw new BusinessException(2001, "请选择问题分类");
        }
    }

    private Qa mustGet(Long id) {
        Qa qa = qaMapper.selectById(id);
        if (qa == null) {
            throw new BusinessException(2002, "问题不存在");
        }
        return qa;
    }

    private void checkQuestioner(Qa qa, Long userId) {
        if (!qa.getQuestionerId().equals(userId)) {
            throw new BusinessException(2003, "只能操作自己的问题");
        }
    }

    /** 判断 user.id 是否为专家（expert 表存在记录） */
    private boolean isExpert(Long userId) {
        return expertMapper.selectCount(
                new LambdaQueryWrapper<Expert>().eq(Expert::getUserId, userId)) > 0;
    }

    private Map<Long, User> userMap(Set<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
    }

    private UserBriefVO toBrief(User u) {
        if (u == null) {
            return null;
        }
        UserBriefVO v = new UserBriefVO();
        v.setId(u.getId());
        v.setRealName(u.getRealName());
        v.setFarmName(u.getFarmName());
        return v;
    }

    private QaListVO toListVO(Qa qa, Map<Long, User> userMap) {
        QaListVO vo = new QaListVO();
        vo.setId(qa.getId());
        vo.setTitle(qa.getTitle());
        vo.setCategoryId(qa.getCategoryId());
        vo.setStatus(qa.getStatus());
        vo.setViewCount(qa.getViewCount());
        vo.setReplyCount(qa.getReplyCount());
        vo.setQuestioner(toBrief(userMap.get(qa.getQuestionerId())));
        vo.setExpert(qa.getExpertId() == null ? null : toBrief(userMap.get(qa.getExpertId())));
        vo.setCreateTime(qa.getCreateTime());
        return vo;
    }

    private QaReplyVO toReplyVO(QaReply r, User replier) {
        QaReplyVO vo = new QaReplyVO();
        vo.setId(r.getId());
        vo.setContent(r.getContent());
        vo.setIsExpert(r.getIsExpert());
        vo.setReplier(toBrief(replier));
        vo.setCreateTime(r.getCreateTime());
        return vo;
    }
}
