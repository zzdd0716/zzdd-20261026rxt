package cn.lcj.rongxiaotong20260918.service;

import cn.lcj.rongxiaotong20260918.common.PageResult;
import cn.lcj.rongxiaotong20260918.pojo.dto.ExpertProfileDTO;
import cn.lcj.rongxiaotong20260918.pojo.vo.ExpertDateSlotsVO;
import cn.lcj.rongxiaotong20260918.pojo.vo.ExpertDetailVO;
import cn.lcj.rongxiaotong20260918.pojo.vo.ExpertListVO;
import cn.lcj.rongxiaotong20260918.pojo.vo.ExpertProfileVO;

import java.util.List;

public interface ExpertService {

    /**
     * 专家列表（公开，仅展示审核通过的专家）
     */
    PageResult<ExpertListVO> list(String keyword, Integer pageNum, Integer pageSize);

    /**
     * 专家详情（公开，含未来14天忙碌时段）
     */
    ExpertDetailVO detail(Long expertId);

    /**
     * 专家自己的资料（需登录且为专家）
     */
    ExpertProfileVO profile(Long userId);

    /**
     * 专家完善自己的资料（需登录且为专家）
     */
    void updateProfile(Long userId, ExpertProfileDTO dto);

    /**
     * 专家可预约时间（公开，未来14天，含已有预约冲突检测）
     */
    List<ExpertDateSlotsVO> availableTime(Long expertId);
}
