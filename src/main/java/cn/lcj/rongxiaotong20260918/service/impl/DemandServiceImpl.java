package cn.lcj.rongxiaotong20260918.service.impl;

import cn.lcj.rongxiaotong20260918.common.BusinessException;
import cn.lcj.rongxiaotong20260918.common.PageResult;
import cn.lcj.rongxiaotong20260918.mapper.CategoryMapper;
import cn.lcj.rongxiaotong20260918.mapper.DemandMapper;
import cn.lcj.rongxiaotong20260918.mapper.UserMapper;
import cn.lcj.rongxiaotong20260918.pojo.dto.DemandDTO;
import cn.lcj.rongxiaotong20260918.pojo.entity.Category;
import cn.lcj.rongxiaotong20260918.pojo.entity.Demand;
import cn.lcj.rongxiaotong20260918.pojo.entity.User;
import cn.lcj.rongxiaotong20260918.pojo.vo.DemandDetailVO;
import cn.lcj.rongxiaotong20260918.pojo.vo.DemandListVO;
import cn.lcj.rongxiaotong20260918.pojo.vo.UserBriefVO;
import cn.lcj.rongxiaotong20260918.service.DemandService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 采购需求 模块实现
 * 错误码段：2001-2003（B 交易）
 */
@Service
public class DemandServiceImpl implements DemandService {

    /** 需求默认状态：进行中 */
    private static final int STATUS_ONGOING = 1;
    /** 商品分类 type=1（需求分类复用商品分类） */
    private static final int CATEGORY_TYPE_PRODUCT = 1;

    @Autowired
    private DemandMapper demandMapper;
    @Autowired
    private CategoryMapper categoryMapper;
    @Autowired
    private UserMapper userMapper;

    @Override
    public PageResult<DemandListVO> pageList(String keyword, Long categoryId, Integer status,
                                             Integer pageNum, Integer pageSize) {
        int num = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int size = pageSize == null || pageSize < 1 ? 10 : pageSize;

        // 先判空再 trim，避免参数求值时 NPE
        String kw = StringUtils.hasText(keyword) ? keyword.trim() : null;

        LambdaQueryWrapper<Demand> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(StringUtils.hasText(kw), w -> w.like(Demand::getName, kw)
                        .or().like(Demand::getDescription, kw))
                .eq(categoryId != null, Demand::getCategoryId, categoryId)
                .eq(status != null, Demand::getStatus, status)
                .orderByDesc(Demand::getCreateTime);

        Page<Demand> page = demandMapper.selectPage(new Page<>(num, size), wrapper);

        List<Demand> records = page.getRecords();
        // 批量联查买家信息（需求列表不返回分类名，按文档字段）
        Map<Long, User> userMap = userMap(records);

        List<DemandListVO> list = records.stream()
                .map(d -> toListVO(d, userMap))
                .collect(Collectors.toList());
        return PageResult.of(page, list);
    }

    @Override
    public DemandDetailVO getDetail(Long id) {
        Demand d = demandMapper.selectById(id);
        if (d == null) {
            throw new BusinessException(2002, "需求不存在");
        }
        User buyer = userMapper.selectById(d.getBuyerId());

        DemandDetailVO vo = new DemandDetailVO();
        vo.setId(d.getId());
        vo.setBuyerId(d.getBuyerId());
        vo.setName(d.getName());
        vo.setDescription(d.getDescription());
        vo.setQuantity(d.getQuantity());
        vo.setUnit(d.getUnit());
        vo.setExpectedPrice(d.getExpectedPrice());
        vo.setCategoryId(d.getCategoryId());
        vo.setDeadline(d.getDeadline());
        vo.setStatus(d.getStatus());
        vo.setBuyer(toBuyerDetail(buyer));
        vo.setCreateTime(d.getCreateTime());
        return vo;
    }

    @Override
    public Long create(Long userId, DemandDTO dto) {
        requireLogin(userId);
        // 必填校验
        if (!StringUtils.hasText(dto.getName())) {
            throw new BusinessException(2001, "需求名称不能为空");
        }
        if (dto.getCategoryId() == null || !isProductCategory(dto.getCategoryId())) {
            throw new BusinessException(2001, "分类不存在");
        }
        if (dto.getQuantity() == null || dto.getQuantity() <= 0) {
            throw new BusinessException(2001, "需求数量必须大于0");
        }
        if (dto.getExpectedPrice() != null && dto.getExpectedPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(2001, "期望单价必须大于0");
        }

        Demand d = new Demand();
        d.setBuyerId(userId);
        d.setName(dto.getName().trim());
        d.setCategoryId(dto.getCategoryId());
        d.setQuantity(dto.getQuantity());
        d.setUnit(StringUtils.hasText(dto.getUnit()) ? dto.getUnit().trim() : "斤");
        d.setExpectedPrice(dto.getExpectedPrice());
        d.setDescription(dto.getDescription());
        d.setDeadline(dto.getDeadline());
        d.setStatus(STATUS_ONGOING); // 发布后默认进行中
        demandMapper.insert(d);
        return d.getId();
    }

    @Override
    public void update(Long userId, Long id, DemandDTO dto) {
        requireLogin(userId);
        Demand exist = demandMapper.selectById(id);
        if (exist == null) {
            throw new BusinessException(2002, "需求不存在");
        }
        if (!exist.getBuyerId().equals(userId)) {
            throw new BusinessException(2003, "只能修改自己的需求");
        }
        if (dto.getCategoryId() != null && !isProductCategory(dto.getCategoryId())) {
            throw new BusinessException(2001, "分类不存在");
        }
        if (dto.getQuantity() != null && dto.getQuantity() <= 0) {
            throw new BusinessException(2001, "需求数量必须大于0");
        }
        if (dto.getExpectedPrice() != null && dto.getExpectedPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(2001, "期望单价必须大于0");
        }

        Demand update = new Demand();
        update.setId(id);
        update.setName(StringUtils.hasText(dto.getName()) ? dto.getName().trim() : null);
        update.setCategoryId(dto.getCategoryId());
        update.setQuantity(dto.getQuantity());
        update.setUnit(StringUtils.hasText(dto.getUnit()) ? dto.getUnit().trim() : null);
        update.setExpectedPrice(dto.getExpectedPrice());
        update.setDescription(dto.getDescription());
        update.setDeadline(dto.getDeadline());
        // updateById 默认只更新非 null 字段，"只传要修改的字段"语义
        demandMapper.updateById(update);
    }

    @Override
    public void delete(Long userId, Long id) {
        requireLogin(userId);
        Demand exist = demandMapper.selectById(id);
        if (exist == null) {
            throw new BusinessException(2002, "需求不存在");
        }
        if (!exist.getBuyerId().equals(userId)) {
            throw new BusinessException(2003, "只能删除自己的需求");
        }
        demandMapper.deleteById(id);
    }

    @Override
    public PageResult<Demand> myList(Long userId, Integer pageNum, Integer pageSize) {
        requireLogin(userId);
        int num = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int size = pageSize == null || pageSize < 1 ? 10 : pageSize;

        LambdaQueryWrapper<Demand> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Demand::getBuyerId, userId)
                .orderByDesc(Demand::getCreateTime);
        Page<Demand> page = demandMapper.selectPage(new Page<>(num, size), wrapper);
        return PageResult.of(page, page.getRecords());
    }

    // ---------- 私有辅助方法 ----------

    private void requireLogin(Long userId) {
        if (userId == null) {
            throw new BusinessException(2001, "未登录或登录已过期");
        }
    }

    /** 校验分类存在且为商品分类（type=1） */
    private boolean isProductCategory(Long categoryId) {
        Category c = categoryMapper.selectById(categoryId);
        return c != null && Objects.equals(c.getType(), CATEGORY_TYPE_PRODUCT);
    }

    /** 批量查询需求买家，得到 id -> user 映射 */
    private Map<Long, User> userMap(List<Demand> records) {
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

    /** 需求行 -> 列表VO */
    private DemandListVO toListVO(Demand d, Map<Long, User> userMap) {
        DemandListVO vo = new DemandListVO();
        vo.setId(d.getId());
        vo.setBuyerId(d.getBuyerId());
        vo.setName(d.getName());
        vo.setQuantity(d.getQuantity());
        vo.setUnit(d.getUnit());
        vo.setExpectedPrice(d.getExpectedPrice());
        vo.setDeadline(d.getDeadline());
        vo.setStatus(d.getStatus());
        User buyer = userMap.get(d.getBuyerId());
        if (buyer != null) {
            UserBriefVO bv = new UserBriefVO();
            bv.setId(buyer.getId());
            bv.setRealName(buyer.getRealName());
            bv.setFarmName(buyer.getFarmName());
            vo.setBuyer(bv);
        }
        vo.setCreateTime(d.getCreateTime());
        return vo;
    }

    /** 买家 -> 详情用完整VO（含 username/phone） */
    private UserBriefVO toBuyerDetail(User buyer) {
        if (buyer == null) {
            return null;
        }
        UserBriefVO bv = new UserBriefVO();
        bv.setId(buyer.getId());
        bv.setUsername(buyer.getUsername());
        bv.setRealName(buyer.getRealName());
        bv.setFarmName(buyer.getFarmName());
        bv.setPhone(buyer.getPhone());
        return bv;
    }
}
