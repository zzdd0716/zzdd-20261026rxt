package cn.lcj.rongxiaotong20260918.service;

import cn.lcj.rongxiaotong20260918.pojo.dto.CartAddDTO;
import cn.lcj.rongxiaotong20260918.pojo.dto.CartSelectDTO;
import cn.lcj.rongxiaotong20260918.pojo.vo.CartItemVO;

import java.util.List;

public interface CartService {

    /**
     * 购物车列表（需登录）
     */
    List<CartItemVO> list(Long userId);

    /**
     * 加入购物车（需登录，商品必须审核通过且上架，数量不超库存；重复加购自动累加）
     */
    void add(Long userId, CartAddDTO dto);

    /**
     * 修改数量（需登录，校验购物车项归属）
     */
    void updateQuantity(Long userId, Long id, Integer quantity);

    /**
     * 删除购物车项（需登录，校验归属）
     */
    void delete(Long userId, Long id);

    /**
     * 批量勾选/取消（需登录，校验归属）
     */
    void selectBatch(Long userId, CartSelectDTO dto);
}
