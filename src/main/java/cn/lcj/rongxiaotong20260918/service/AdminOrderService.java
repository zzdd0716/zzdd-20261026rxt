package cn.lcj.rongxiaotong20260918.service;

import cn.lcj.rongxiaotong20260918.common.PageResult;
import cn.lcj.rongxiaotong20260918.pojo.vo.AdminOrderListVO;

public interface AdminOrderService {

    /**
     * 10.1 订单列表（管理员，可按状态/订单号筛选）
     */
    PageResult<AdminOrderListVO> pageList(Long userId, Integer status, String orderNo,
                                          Integer pageNum, Integer pageSize);
}
