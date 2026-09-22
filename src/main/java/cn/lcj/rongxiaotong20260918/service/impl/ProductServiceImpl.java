package cn.lcj.rongxiaotong20260918.service.impl;

import cn.lcj.rongxiaotong20260918.common.BusinessException;
import cn.lcj.rongxiaotong20260918.common.PageResult;
import cn.lcj.rongxiaotong20260918.mapper.CategoryMapper;
import cn.lcj.rongxiaotong20260918.mapper.ProductMapper;
import cn.lcj.rongxiaotong20260918.mapper.UserMapper;
import cn.lcj.rongxiaotong20260918.pojo.dto.ProductDTO;
import cn.lcj.rongxiaotong20260918.pojo.entity.Category;
import cn.lcj.rongxiaotong20260918.pojo.entity.Product;
import cn.lcj.rongxiaotong20260918.pojo.entity.User;
import cn.lcj.rongxiaotong20260918.pojo.vo.ProductDetailVO;
import cn.lcj.rongxiaotong20260918.pojo.vo.ProductListVO;
import cn.lcj.rongxiaotong20260918.pojo.vo.UserBriefVO;
import cn.lcj.rongxiaotong20260918.service.ProductService;
import com.alibaba.fastjson.JSON;
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
 * 商品/货源 模块实现
 * 错误码段：2001-2003（B 交易）
 */
@Service
public class ProductServiceImpl implements ProductService {

    /** 审核状态：待审核 */
    private static final int AUDIT_PENDING = 0;
    /** 审核状态：审核通过 */
    private static final int AUDIT_PASSED = 1;
    /** 上下架状态：上架 */
    private static final int STATUS_ON = 1;
    /** 商品分类 type=1 */
    private static final int CATEGORY_TYPE_PRODUCT = 1;

    @Autowired
    private ProductMapper productMapper;
    @Autowired
    private CategoryMapper categoryMapper;
    @Autowired
    private UserMapper userMapper;

    @Override
    public PageResult<ProductListVO> pageList(String keyword, Long categoryId, String origin,
                                              Integer pageNum, Integer pageSize) {
        int num = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int size = pageSize == null || pageSize < 1 ? 10 : pageSize;

        // 先判空再 trim，避免参数求值时 NPE（条件为 false 时也不会执行 trim）
        String kw = StringUtils.hasText(keyword) ? keyword.trim() : null;
        String org = StringUtils.hasText(origin) ? origin.trim() : null;

        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Product::getAuditStatus, AUDIT_PASSED) // 只展示审核通过
                .eq(Product::getStatus, STATUS_ON)        // 只展示上架
                .and(StringUtils.hasText(kw), w -> w.like(Product::getName, kw)
                        .or().like(Product::getOrigin, kw))
                .eq(categoryId != null, Product::getCategoryId, categoryId)
                .eq(StringUtils.hasText(org), Product::getOrigin, org)
                .orderByDesc(Product::getCreateTime);

        Page<Product> page = productMapper.selectPage(new Page<>(num, size), wrapper);

        List<Product> records = page.getRecords();
        // 批量联查分类名、卖家信息，避免逐条查询（N+1）
        Map<Long, Category> categoryMap = categoryMap(records);
        Map<Long, User> userMap = userMap(records);

        List<ProductListVO> list = records.stream()
                .map(p -> toListVO(p, categoryMap, userMap))
                .collect(Collectors.toList());
        return PageResult.of(page, list);
    }

    @Override
    public ProductDetailVO getDetail(Long id) {
        Product p = productMapper.selectById(id);
        if (p == null) {
            throw new BusinessException(2002, "商品不存在");
        }
        Category category = p.getCategoryId() == null ? null : categoryMapper.selectById(p.getCategoryId());
        User seller = userMapper.selectById(p.getSellerId());

        ProductDetailVO vo = new ProductDetailVO();
        vo.setId(p.getId());
        vo.setSellerId(p.getSellerId());
        vo.setName(p.getName());
        vo.setCategoryId(p.getCategoryId());
        vo.setCategoryName(category == null ? null : category.getName());
        vo.setPrice(p.getPrice());
        vo.setUnit(p.getUnit());
        vo.setStock(p.getStock());
        vo.setImage(p.getImage());
        vo.setImages(parseImages(p.getImages()));
        vo.setDescription(p.getDescription());
        vo.setOrigin(p.getOrigin());
        vo.setAuditStatus(p.getAuditStatus());
        vo.setStatus(p.getStatus());
        vo.setSeller(toSellerDetail(seller));
        vo.setCreateTime(p.getCreateTime());
        return vo;
    }

    @Override
    public Long create(Long userId, ProductDTO dto) {
        requireLogin(userId);
        // 必填校验
        if (!StringUtils.hasText(dto.getName())) {
            throw new BusinessException(2001, "商品名称不能为空");
        }
        if (dto.getCategoryId() == null || !isProductCategory(dto.getCategoryId())) {
            throw new BusinessException(2001, "商品分类不存在");
        }
        if (dto.getPrice() == null || dto.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(2001, "单价必须大于0");
        }
        if (dto.getStock() == null || dto.getStock() < 0) {
            throw new BusinessException(2001, "库存不能小于0");
        }
        if (!StringUtils.hasText(dto.getImage())) {
            throw new BusinessException(2001, "请上传商品主图");
        }

        Product p = new Product();
        p.setSellerId(userId);
        p.setName(dto.getName().trim());
        p.setCategoryId(dto.getCategoryId());
        p.setPrice(dto.getPrice());
        p.setUnit(StringUtils.hasText(dto.getUnit()) ? dto.getUnit().trim() : "斤");
        p.setStock(dto.getStock());
        p.setImage(dto.getImage().trim());
        p.setImages(dto.getImages());
        p.setDescription(dto.getDescription());
        p.setOrigin(dto.getOrigin());
        p.setAuditStatus(AUDIT_PENDING); // 发布后进入待审核，由管理员审核
        p.setStatus(STATUS_ON);          // 默认上架（审核通过后即可在公开列表看到）
        productMapper.insert(p);
        return p.getId();
    }

    @Override
    public void update(Long userId, Long id, ProductDTO dto) {
        requireLogin(userId);
        Product exist = productMapper.selectById(id);
        if (exist == null) {
            throw new BusinessException(2002, "商品不存在");
        }
        if (!exist.getSellerId().equals(userId)) {
            throw new BusinessException(2003, "只能修改自己的货源");
        }
        if (dto.getCategoryId() != null && !isProductCategory(dto.getCategoryId())) {
            throw new BusinessException(2001, "商品分类不存在");
        }
        if (dto.getPrice() != null && dto.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(2001, "单价必须大于0");
        }
        if (dto.getStock() != null && dto.getStock() < 0) {
            throw new BusinessException(2001, "库存不能小于0");
        }

        Product update = new Product();
        update.setId(id);
        update.setName(StringUtils.hasText(dto.getName()) ? dto.getName().trim() : null);
        update.setCategoryId(dto.getCategoryId());
        update.setPrice(dto.getPrice());
        update.setUnit(StringUtils.hasText(dto.getUnit()) ? dto.getUnit().trim() : null);
        update.setStock(dto.getStock());
        update.setImage(StringUtils.hasText(dto.getImage()) ? dto.getImage().trim() : null);
        update.setImages(dto.getImages());
        update.setDescription(dto.getDescription());
        update.setOrigin(dto.getOrigin());
        // updateById 默认只更新非 null 字段，"只传要修改的字段"语义
        productMapper.updateById(update);
    }

    @Override
    public void updateStatus(Long userId, Long id, Integer status) {
        requireLogin(userId);
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException(2001, "状态参数错误：0=下架 1=上架");
        }
        Product exist = productMapper.selectById(id);
        if (exist == null) {
            throw new BusinessException(2002, "商品不存在");
        }
        if (!exist.getSellerId().equals(userId)) {
            throw new BusinessException(2003, "只能操作自己的货源");
        }
        Product update = new Product();
        update.setId(id);
        update.setStatus(status);
        productMapper.updateById(update);
    }

    @Override
    public void delete(Long userId, Long id) {
        requireLogin(userId);
        Product exist = productMapper.selectById(id);
        if (exist == null) {
            throw new BusinessException(2002, "商品不存在");
        }
        if (!exist.getSellerId().equals(userId)) {
            throw new BusinessException(2003, "只能删除自己的货源");
        }
        productMapper.deleteById(id);
    }

    @Override
    public PageResult<Product> myList(Long userId, Integer auditStatus, Integer status,
                                      Integer pageNum, Integer pageSize) {
        requireLogin(userId);
        int num = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int size = pageSize == null || pageSize < 1 ? 10 : pageSize;

        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Product::getSellerId, userId)
                .eq(auditStatus != null, Product::getAuditStatus, auditStatus)
                .eq(status != null, Product::getStatus, status)
                .orderByDesc(Product::getCreateTime);
        Page<Product> page = productMapper.selectPage(new Page<>(num, size), wrapper);
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

    /** 批量查询商品所属分类，得到 id -> category 映射 */
    private Map<Long, Category> categoryMap(List<Product> records) {
        Set<Long> ids = records.stream()
                .map(Product::getCategoryId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return categoryMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Category::getId, Function.identity()));
    }

    /** 批量查询商品卖家，得到 id -> user 映射 */
    private Map<Long, User> userMap(List<Product> records) {
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

    /** 商品行 -> 列表VO */
    private ProductListVO toListVO(Product p, Map<Long, Category> categoryMap, Map<Long, User> userMap) {
        ProductListVO vo = new ProductListVO();
        vo.setId(p.getId());
        vo.setName(p.getName());
        vo.setCategoryId(p.getCategoryId());
        Category c = p.getCategoryId() == null ? null : categoryMap.get(p.getCategoryId());
        vo.setCategoryName(c == null ? null : c.getName());
        vo.setPrice(p.getPrice());
        vo.setUnit(p.getUnit());
        vo.setStock(p.getStock());
        vo.setImage(p.getImage());
        vo.setOrigin(p.getOrigin());
        vo.setCreateTime(p.getCreateTime());
        User seller = userMap.get(p.getSellerId());
        if (seller != null) {
            UserBriefVO sv = new UserBriefVO();
            sv.setId(seller.getId());
            sv.setRealName(seller.getRealName());
            sv.setFarmName(seller.getFarmName());
            vo.setSeller(sv);
        }
        return vo;
    }

    /** 卖家 -> 详情用完整VO（含 username/phone） */
    private UserBriefVO toSellerDetail(User seller) {
        if (seller == null) {
            return null;
        }
        UserBriefVO sv = new UserBriefVO();
        sv.setId(seller.getId());
        sv.setUsername(seller.getUsername());
        sv.setRealName(seller.getRealName());
        sv.setFarmName(seller.getFarmName());
        sv.setPhone(seller.getPhone());
        return sv;
    }

    /** images JSON 字符串 -> 数组，解析失败返回 null */
    private List<String> parseImages(String images) {
        if (!StringUtils.hasText(images)) {
            return null;
        }
        try {
            return JSON.parseArray(images, String.class);
        } catch (Exception e) {
            return null;
        }
    }
}
