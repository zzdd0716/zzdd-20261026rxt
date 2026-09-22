package cn.lcj.rongxiaotong20260918.service;

import cn.lcj.rongxiaotong20260918.common.PageResult;
import cn.lcj.rongxiaotong20260918.pojo.vo.AdminProductListVO;

public interface AdminProductService {

    /**
     * 8.1 商品列表（管理员，可按审核状态/上下架/关键字筛选）
     */
    PageResult<AdminProductListVO> pageList(Long userId, Integer auditStatus, Integer status,
                                            String keyword, Integer pageNum, Integer pageSize);

    /**
     * 8.2 商品审核通过（管理员，仅待审核）
     */
    void approve(Long userId, Long id);

    /**
     * 8.3 商品审核驳回（管理员，仅待审核，驳回原因必填）
     */
    void reject(Long userId, Long id, String auditRemark);

    /**
     * 8.4 商品强制上下架（管理员）
     */
    void updateStatus(Long userId, Long id, Integer status);

    /**
     * 8.5 删除商品（管理员）
     */
    void delete(Long userId, Long id);
}
