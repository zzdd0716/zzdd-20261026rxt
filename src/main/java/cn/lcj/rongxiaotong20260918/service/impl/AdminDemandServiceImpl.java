package cn.lcj.rongxiaotong20260918.service.impl;

import cn.lcj.rongxiaotong20260918.common.BusinessException;
import cn.lcj.rongxiaotong20260918.common.PageResult;
import cn.lcj.rongxiaotong20260918.mapper.DemandMapper;
import cn.lcj.rongxiaotong20260918.mapper.UserMapper;
import cn.lcj.rongxiaotong20260918.pojo.entity.Demand;
import cn.lcj.rongxiaotong20260918.pojo.entity.User;
import cn.lcj.rongxiaotong20260918.pojo.vo.AdminDemandListVO;
import cn.lcj.rongxiaotong20260918.pojo.vo.UserBriefVO;
import cn.lcj.rongxiaotong20260918.service.AdminDemandService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 需求管理员 模块实现（后端 B）
 * 接口文档：02-backend-B-trade-expert.md 第九章
 * 全部接口需管理员（role=3）。
 */
@Service
public class AdminDemandServiceImpl implements AdminDemandService {

    /** 角色：管理员 */
    private static final int ROLE_ADMIN = 3;

    @Autowired
    private DemandMapper demandMapper;
    @Autowired
    private UserMapper userMapper;

    @Override
    public PageResult<AdminDemandListVO> pageList(Long userId, Integer status, String keyword,
                                                  Integer pageNum, Integer pageSize) {
        requireAdmin(userId);
        int num = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int size = pageSize == null || pageSize < 1 ? 10 : pageSize;
        String kw = StringUtils.hasText(keyword) ? keyword.trim() : null;

        LambdaQueryWrapper<Demand> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(status != null, Demand::getStatus, status)
                .like(StringUtils.hasText(kw), Demand::getName, kw)
                .orderByDesc(Demand::getCreateTime);
        Page<Demand> page = demandMapper.selectPage(new Page<>(num, size), wrapper);

        Map<Long, User> userMap = buyerMap(page.getRecords());
        List<AdminDemandListVO> list = page.getRecords().stream()
                .map(d -> toVO(d, userMap))
                .collect(Collectors.toList());
        return PageResult.of(page, list);
    }

    @Override
    public void delete(Long userId, Long id) {
        requireAdmin(userId);
        Demand exist = demandMapper.selectById(id);
        if (exist == null) {
            throw new BusinessException(2002, "需求不存在");
        }
        demandMapper.deleteById(id);
    }

    // ---------- 私有辅助方法 ----------

    private void requireAdmin(Long userId) {
        if (userId == null) {
            throw new BusinessException(2001, "未登录或登录已过期");
        }
        User user = userMapper.selectById(userId);
        if (user == null || !Objects.equals(user.getRole(), ROLE_ADMIN)) {
            throw new BusinessException(2003, "仅管理员可操作");
        }
    }

    private Map<Long, User> buyerMap(List<Demand> records) {
        Set<Long> ids = records.stream()
                .map(Demand::getBuyerId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return userMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
    }

    private AdminDemandListVO toVO(Demand d, Map<Long, User> userMap) {
        AdminDemandListVO vo = new AdminDemandListVO();
        vo.setId(d.getId());
        vo.setName(d.getName());
        vo.setQuantity(d.getQuantity());
        vo.setExpectedPrice(d.getExpectedPrice());
        vo.setStatus(d.getStatus());
        vo.setCreateTime(d.getCreateTime());
        User buyer = d.getBuyerId() == null ? null : userMap.get(d.getBuyerId());
        if (buyer != null) {
            UserBriefVO bv = new UserBriefVO();
            bv.setId(buyer.getId());
            bv.setRealName(buyer.getRealName());
            vo.setBuyer(bv);
        }
        return vo;
    }
}
