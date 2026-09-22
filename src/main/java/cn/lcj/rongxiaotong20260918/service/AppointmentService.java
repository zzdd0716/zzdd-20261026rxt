package cn.lcj.rongxiaotong20260918.service;

import cn.lcj.rongxiaotong20260918.pojo.dto.AppointmentDTO;
import cn.lcj.rongxiaotong20260918.pojo.vo.AppointmentCreateVO;
import cn.lcj.rongxiaotong20260918.pojo.vo.AppointmentVO;

import java.util.List;

public interface AppointmentService {

    /**
     * 创建预约（需登录；专家审核通过 + 时间段冲突检测）
     */
    AppointmentCreateVO create(Long userId, AppointmentDTO dto);

    /**
     * 用户端：我的预约（需登录）
     */
    List<AppointmentVO> myUserList(Long userId, Integer status);

    /**
     * 专家端：我的预约（需登录且为专家）
     */
    List<AppointmentVO> myExpertList(Long userId, Integer status);

    /**
     * 专家确认预约（专家本人，仅待确认）
     */
    void confirm(Long userId, Long id);

    /**
     * 专家标记完成（专家本人，仅已确认）
     */
    void complete(Long userId, Long id);

    /**
     * 取消预约（创建者本人，仅待确认）
     */
    void cancel(Long userId, Long id);
}
