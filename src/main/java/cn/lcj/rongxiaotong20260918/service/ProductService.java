package cn.lcj.rongxiaotong20260918.service;

import cn.lcj.rongxiaotong20260918.common.PageResult;
import cn.lcj.rongxiaotong20260918.pojo.dto.ProductDTO;
import cn.lcj.rongxiaotong20260918.pojo.entity.Product;
import cn.lcj.rongxiaotong20260918.pojo.vo.ProductDetailVO;
import cn.lcj.rongxiaotong20260918.pojo.vo.ProductListVO;

public interface ProductService {

    /**
     * 商品列表（公开）：只展示"审核通过 + 上架"的商品
     */
    PageResult<ProductListVO> pageList(String keyword, Long categoryId, String origin,
                                       Integer pageNum, Integer pageSize);

    /**
     * 商品详情（公开）
     */
    ProductDetailVO getDetail(Long id);

    /**
     * 发布货源（需登录，农民）
     *
     * @return 新商品ID
     */
    Long create(Long userId, ProductDTO dto);

    /**
     * 修改自己的货源（需登录，校验 seller_id == 当前用户）
     */
    void update(Long userId, Long id, ProductDTO dto);

    /**
     * 上架/下架自己的货源（需登录，校验 seller_id == 当前用户）
     */
    void updateStatus(Long userId, Long id, Integer status);

    /**
     * 删除自己的货源（需登录，校验 seller_id == 当前用户）
     */
    void delete(Long userId, Long id);

    /**
     * 我的货源（需登录）
     */
    PageResult<Product> myList(Long userId, Integer auditStatus, Integer status,
                               Integer pageNum, Integer pageSize);
}
