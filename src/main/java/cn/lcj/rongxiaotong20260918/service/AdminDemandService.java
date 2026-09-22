package cn.lcj.rongxiaotong20260918.service;

import cn.lcj.rongxiaotong20260918.common.PageResult;
import cn.lcj.rongxiaotong20260918.pojo.vo.AdminDemandListVO;

public interface AdminDemandService {

    /**
     * 9.1 需求列表（管理员，可按状态/关键字筛选）
     */
    PageResult<AdminDemandListVO> pageList(Long userId, Integer status, String keyword,
                                           Integer pageNum, Integer pageSize);

    /**
     * 9.2 删除需求（管理员）
     */
    void delete(Long userId, Long id);
}
