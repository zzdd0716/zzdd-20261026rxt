package cn.lcj.rongxiaotong20260918.service.impl;

import cn.lcj.rongxiaotong20260918.common.BusinessException;
import cn.lcj.rongxiaotong20260918.common.PageResult;
import cn.lcj.rongxiaotong20260918.mapper.ProductMapper;
import cn.lcj.rongxiaotong20260918.mapper.UserMapper;
import cn.lcj.rongxiaotong20260918.pojo.entity.Product;
import cn.lcj.rongxiaotong20260918.pojo.entity.User;
import cn.lcj.rongxiaotong20260918.pojo.vo.AdminProductListVO;
import cn.lcj.rongxiaotong20260918.pojo.vo.UserBriefVO;
import cn.lcj.rongxiaotong20260918.service.AdminProductService;
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
 * 商品管理员 模块实现（后端 B）
 * 接口文档：02-backend-B-trade-expert.md 第八章
 * 全部接口需管理员（role=3）。
 * 审核状态：0待审核/1通过/2驳回；状态：0下架/1上架
 */
@Service
public class AdminProductServiceImpl implements AdminProductService {

    /** 角色：管理员 */
    private static final int ROLE_ADMIN = 3;
    /** 审核状态 */
    private static final int AUDIT_PENDING = 0;
    private static final int AUDIT_PASSED = 1;
    private static final int AUDIT_REJECTED = 2;
    /** 上下架状态 */
    private static final int STATUS_OFF = 0;
    private static final int STATUS_ON = 1;

    @Autowired
    private ProductMapper productMapper;
    @Autowired
    private UserMapper userMapper;

    @Override
    public PageResult<AdminProductListVO> pageList(Long userId, Integer auditStatus, Integer status,
                                                   String keyword, Integer pageNum, Integer pageSize) {
        requireAdmin(userId);
        int num = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int size = pageSize == null || pageSize < 1 ? 10 : pageSize;
        String kw = StringUtils.hasText(keyword) ? keyword.trim() : null;

        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(auditStatus != null, Product::getAuditStatus, auditStatus)
                .eq(status != null, Product::getStatus, status)
                .like(StringUtils.hasText(kw), Product::getName, kw)
                .orderByDesc(Product::getCreateTime);
        Page<Product> page = productMapper.selectPage(new Page<>(num, size), wrapper);

        Map<Long, User> userMap = sellerMap(page.getRecords());
        List<AdminProductListVO> list = page.getRecords().stream()
                .map(p -> toVO(p, userMap))
                .collect(Collectors.toList());
        return PageResult.of(page, list);
    }

    @Override
    public void approve(Long userId, Long id) {
        requireAdmin(userId);
        Product exist = mustGet(id);
        if (exist.getAuditStatus() != AUDIT_PENDING) {
            throw new BusinessException(2005, "仅待审核商品可审核");
        }
        Product update = new Product();
        update.setId(id);
        update.setAuditStatus(AUDIT_PASSED);
        update.setAuditRemark(null); // 通过时清空驳回原因
        productMapper.updateById(update);
    }

    @Override
    public void reject(Long userId, Long id, String auditRemark) {
        requireAdmin(userId);
        Product exist = mustGet(id);
        if (!StringUtils.hasText(auditRemark)) {
            throw new BusinessException(2001, "请填写驳回原因");
        }
        if (auditRemark.trim().length() > 255) {
            throw new BusinessException(2001, "驳回原因不能超过255个字符");
        }
        if (exist.getAuditStatus() != AUDIT_PENDING) {
            throw new BusinessException(2005, "仅待审核商品可审核");
        }
        Product update = new Product();
        update.setId(id);
        update.setAuditStatus(AUDIT_REJECTED);
        update.setAuditRemark(auditRemark.trim());
        productMapper.updateById(update);
    }

    @Override
    public void updateStatus(Long userId, Long id, Integer status) {
        requireAdmin(userId);
        if (status == null || (status != STATUS_OFF && status != STATUS_ON)) {
            throw new BusinessException(2001, "状态参数错误：0=下架 1=上架");
        }
        mustGet(id);
        Product update = new Product();
        update.setId(id);
        update.setStatus(status);
        productMapper.updateById(update);
    }

    @Override
    public void delete(Long userId, Long id) {
        requireAdmin(userId);
        mustGet(id);
        productMapper.deleteById(id);
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

    private Product mustGet(Long id) {
        Product p = productMapper.selectById(id);
        if (p == null) {
            throw new BusinessException(2002, "商品不存在");
        }
        return p;
    }

    private Map<Long, User> sellerMap(List<Product> records) {
        Set<Long> ids = records.stream()
                .map(Product::getSellerId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return userMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
    }

    private AdminProductListVO toVO(Product p, Map<Long, User> userMap) {
        AdminProductListVO vo = new AdminProductListVO();
        vo.setId(p.getId());
        vo.setName(p.getName());
        vo.setPrice(p.getPrice());
        vo.setStock(p.getStock());
        vo.setImage(p.getImage());
        vo.setAuditStatus(p.getAuditStatus());
        vo.setAuditRemark(p.getAuditRemark());
        vo.setStatus(p.getStatus());
        vo.setCreateTime(p.getCreateTime());
        User seller = p.getSellerId() == null ? null : userMap.get(p.getSellerId());
        if (seller != null) {
            UserBriefVO sv = new UserBriefVO();
            sv.setId(seller.getId());
            sv.setRealName(seller.getRealName());
            vo.setSeller(sv);
        }
        return vo;
    }
}
