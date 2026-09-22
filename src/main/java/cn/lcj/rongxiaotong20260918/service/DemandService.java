package cn.lcj.rongxiaotong20260918.service;

import cn.lcj.rongxiaotong20260918.common.PageResult;
import cn.lcj.rongxiaotong20260918.pojo.dto.DemandDTO;
import cn.lcj.rongxiaotong20260918.pojo.entity.Demand;
import cn.lcj.rongxiaotong20260918.pojo.vo.DemandDetailVO;
import cn.lcj.rongxiaotong20260918.pojo.vo.DemandListVO;

public interface DemandService {

    /**
     * 需求列表（公开）
     */
    PageResult<DemandListVO> pageList(String keyword, Long categoryId, Integer status,
                                      Integer pageNum, Integer pageSize);

    /**
     * 需求详情（公开）
     */
    DemandDetailVO getDetail(Long id);

    /**
     * 发布需求（需登录）
     *
     * @return 新需求ID
     */
    Long create(Long userId, DemandDTO dto);

    /**
     * 修改需求（需登录，校验 buyer_id == 当前用户）
     */
    void update(Long userId, Long id, DemandDTO dto);

    /**
     * 删除需求（需登录，校验 buyer_id == 当前用户）
     */
    void delete(Long userId, Long id);

    /**
     * 我的需求（需登录）
     */
    PageResult<Demand> myList(Long userId, Integer pageNum, Integer pageSize);
}
