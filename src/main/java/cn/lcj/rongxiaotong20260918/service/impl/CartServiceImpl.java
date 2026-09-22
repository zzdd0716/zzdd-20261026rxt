package cn.lcj.rongxiaotong20260918.service.impl;

import cn.lcj.rongxiaotong20260918.common.BusinessException;
import cn.lcj.rongxiaotong20260918.mapper.CartMapper;
import cn.lcj.rongxiaotong20260918.mapper.ProductMapper;
import cn.lcj.rongxiaotong20260918.pojo.dto.CartAddDTO;
import cn.lcj.rongxiaotong20260918.pojo.dto.CartSelectDTO;
import cn.lcj.rongxiaotong20260918.pojo.entity.Cart;
import cn.lcj.rongxiaotong20260918.pojo.entity.Product;
import cn.lcj.rongxiaotong20260918.pojo.vo.CartItemVO;
import cn.lcj.rongxiaotong20260918.pojo.vo.ProductBriefVO;
import cn.lcj.rongxiaotong20260918.service.CartService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 购物车 模块实现
 * 错误码段：2001-2004（B 交易）
 */
@Service
public class CartServiceImpl implements CartService {

    /** 审核状态：审核通过 */
    private static final int AUDIT_PASSED = 1;
    /** 上下架状态：上架 */
    private static final int STATUS_ON = 1;

    @Autowired
    private CartMapper cartMapper;
    @Autowired
    private ProductMapper productMapper;

    @Override
    public List<CartItemVO> list(Long userId) {
        requireLogin(userId);
        LambdaQueryWrapper<Cart> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Cart::getUserId, userId)
                .orderByDesc(Cart::getCreateTime);
        List<Cart> carts = cartMapper.selectList(wrapper);

        // 批量联查商品，避免 N+1
        Set<Long> productIds = carts.stream()
                .map(Cart::getProductId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, Product> productMap = productIds.isEmpty() ? Collections.emptyMap()
                : productMapper.selectBatchIds(productIds).stream()
                        .collect(Collectors.toMap(Product::getId, Function.identity()));

        return carts.stream()
                .map(c -> toVO(c, productMap.get(c.getProductId())))
                .collect(Collectors.toList());
    }

    @Override
    public void add(Long userId, CartAddDTO dto) {
        requireLogin(userId);
        if (dto.getProductId() == null) {
            throw new BusinessException(2001, "商品ID不能为空");
        }
        if (dto.getQuantity() == null || dto.getQuantity() <= 0) {
            throw new BusinessException(2001, "数量必须大于0");
        }

        Product product = productMapper.selectById(dto.getProductId());
        if (product == null) {
            throw new BusinessException(2002, "商品不存在");
        }
        // 只有"审核通过 + 上架"的商品可以加入购物车（保证能正常下单）
        if (!Objects.equals(product.getAuditStatus(), AUDIT_PASSED)
                || !Objects.equals(product.getStatus(), STATUS_ON)) {
            throw new BusinessException(2001, "商品已下架或未通过审核");
        }

        // 同一用户同一商品已存在则数量累加
        Cart exist = cartMapper.selectOne(new LambdaQueryWrapper<Cart>()
                .eq(Cart::getUserId, userId)
                .eq(Cart::getProductId, dto.getProductId())
                .last("limit 1"));
        int newQuantity = exist == null ? dto.getQuantity() : exist.getQuantity() + dto.getQuantity();
        if (newQuantity > product.getStock()) {
            throw new BusinessException(2004, "库存不足，当前库存 " + product.getStock());
        }

        if (exist == null) {
            Cart cart = new Cart();
            cart.setUserId(userId);
            cart.setProductId(dto.getProductId());
            cart.setQuantity(dto.getQuantity());
            cart.setSelected(1); // 默认勾选
            cartMapper.insert(cart);
        } else {
            Cart update = new Cart();
            update.setId(exist.getId());
            update.setQuantity(newQuantity);
            cartMapper.updateById(update);
        }
    }

    @Override
    public void updateQuantity(Long userId, Long id, Integer quantity) {
        requireLogin(userId);
        if (quantity == null || quantity <= 0) {
            throw new BusinessException(2001, "数量必须大于0");
        }
        Cart cart = getOwnedCart(userId, id);

        Product product = productMapper.selectById(cart.getProductId());
        if (product == null) {
            throw new BusinessException(2002, "商品不存在");
        }
        if (quantity > product.getStock()) {
            throw new BusinessException(2004, "库存不足，当前库存 " + product.getStock());
        }

        Cart update = new Cart();
        update.setId(id);
        update.setQuantity(quantity);
        cartMapper.updateById(update);
    }

    @Override
    public void delete(Long userId, Long id) {
        requireLogin(userId);
        Cart cart = getOwnedCart(userId, id);
        cartMapper.deleteById(cart.getId());
    }

    @Override
    public void selectBatch(Long userId, CartSelectDTO dto) {
        requireLogin(userId);
        if (dto.getIds() == null || dto.getIds().isEmpty()) {
            throw new BusinessException(2001, "请选择要操作的购物车项");
        }
        if (dto.getSelected() == null || (dto.getSelected() != 0 && dto.getSelected() != 1)) {
            throw new BusinessException(2001, "勾选参数错误：0=取消 1=勾选");
        }

        List<Cart> carts = cartMapper.selectBatchIds(dto.getIds());
        if (carts.size() != dto.getIds().size()) {
            throw new BusinessException(2002, "部分购物车项不存在");
        }
        for (Cart c : carts) {
            if (!c.getUserId().equals(userId)) {
                throw new BusinessException(2003, "只能操作自己的购物车");
            }
        }
        for (Cart c : carts) {
            Cart update = new Cart();
            update.setId(c.getId());
            update.setSelected(dto.getSelected());
            cartMapper.updateById(update);
        }
    }

    // ---------- 私有辅助方法 ----------

    private void requireLogin(Long userId) {
        if (userId == null) {
            throw new BusinessException(2001, "未登录或登录已过期");
        }
    }

    /** 查询当前用户拥有的购物车项，不存在或非本人时抛异常 */
    private Cart getOwnedCart(Long userId, Long id) {
        Cart cart = cartMapper.selectById(id);
        if (cart == null) {
            throw new BusinessException(2002, "购物车项不存在");
        }
        if (!cart.getUserId().equals(userId)) {
            throw new BusinessException(2003, "只能操作自己的购物车");
        }
        return cart;
    }

    /** 购物车行 -> 列表VO */
    private CartItemVO toVO(Cart c, Product p) {
        CartItemVO vo = new CartItemVO();
        vo.setId(c.getId());
        vo.setProductId(c.getProductId());
        vo.setQuantity(c.getQuantity());
        vo.setSelected(c.getSelected());
        vo.setCreateTime(c.getCreateTime());
        if (p != null) {
            ProductBriefVO pb = new ProductBriefVO();
            pb.setName(p.getName());
            pb.setPrice(p.getPrice());
            pb.setImage(p.getImage());
            pb.setStock(p.getStock());
            vo.setProduct(pb);
        }
        return vo;
    }
}
