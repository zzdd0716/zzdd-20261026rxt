package cn.lcj.rongxiaotong20260918.service.impl;

import cn.lcj.rongxiaotong20260918.common.BusinessException;
import cn.lcj.rongxiaotong20260918.common.PageResult;
import cn.lcj.rongxiaotong20260918.mapper.OrdersMapper;
import cn.lcj.rongxiaotong20260918.mapper.UserMapper;
import cn.lcj.rongxiaotong20260918.pojo.entity.Orders;
import cn.lcj.rongxiaotong20260918.pojo.entity.User;
import cn.lcj.rongxiaotong20260918.pojo.vo.AdminOrderListVO;
import cn.lcj.rongxiaotong20260918.pojo.vo.UserBriefVO;
import cn.lcj.rongxiaotong20260918.service.AdminOrderService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 订单管理员 模块实现（后端 B）
 * 接口文档：02-backend-B-trade-expert.md 第十章
 * 需管理员（role=3）。
 */
@Service
public class AdminOrderServiceImpl implements AdminOrderService {

    /** 角色：管理员 */
    private static final int ROLE_ADMIN = 3;

    @Autowired
    private OrdersMapper ordersMapper;
    @Autowired
    private UserMapper userMapper;

    @Override
    public PageResult<AdminOrderListVO> pageList(Long userId, Integer status, String orderNo,
                                                 Integer pageNum, Integer pageSize) {
        requireAdmin(userId);
        int num = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int size = pageSize == null || pageSize < 1 ? 10 : pageSize;
        String no = StringUtils.hasText(orderNo) ? orderNo.trim() : null;

        LambdaQueryWrapper<Orders> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(status != null, Orders::getStatus, status)
                .like(StringUtils.hasText(no), Orders::getOrderNo, no)
                .orderByDesc(Orders::getCreateTime);
        Page<Orders> page = ordersMapper.selectPage(new Page<>(num, size), wrapper);

        // 批量联查买家 + 卖家（去重后一次查询）
        Set<Long> userIds = new HashSet<>();
        for (Orders o : page.getRecords()) {
            if (o.getBuyerId() != null) userIds.add(o.getBuyerId());
            if (o.getSellerId() != null) userIds.add(o.getSellerId());
        }
        Map<Long, User> userMap = userIds.isEmpty() ? Collections.emptyMap()
                : userMapper.selectBatchIds(userIds).stream()
                        .collect(Collectors.toMap(User::getId, Function.identity()));

        List<AdminOrderListVO> list = page.getRecords().stream()
                .map(o -> toVO(o, userMap))
                .collect(Collectors.toList());
        return PageResult.of(page, list);
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

    private AdminOrderListVO toVO(Orders o, Map<Long, User> userMap) {
        AdminOrderListVO vo = new AdminOrderListVO();
        vo.setId(o.getId());
        vo.setOrderNo(o.getOrderNo());
        vo.setTotalAmount(o.getTotalAmount());
        vo.setStatus(o.getStatus());
        vo.setRefundStatus(o.getRefundStatus());
        vo.setCreateTime(o.getCreateTime());
        vo.setBuyer(toBrief(userMap.get(o.getBuyerId())));
        vo.setSeller(toBrief(userMap.get(o.getSellerId())));
        return vo;
    }

    private UserBriefVO toBrief(User user) {
        if (user == null) {
            return null;
        }
        UserBriefVO v = new UserBriefVO();
        v.setId(user.getId());
        v.setRealName(user.getRealName());
        return v;
    }
}
