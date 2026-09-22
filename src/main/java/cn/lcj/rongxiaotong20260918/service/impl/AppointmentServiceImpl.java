package cn.lcj.rongxiaotong20260918.service.impl;

import cn.lcj.rongxiaotong20260918.common.BusinessException;
import cn.lcj.rongxiaotong20260918.mapper.AppointmentMapper;
import cn.lcj.rongxiaotong20260918.mapper.ExpertMapper;
import cn.lcj.rongxiaotong20260918.mapper.UserMapper;
import cn.lcj.rongxiaotong20260918.pojo.dto.AppointmentDTO;
import cn.lcj.rongxiaotong20260918.pojo.entity.Appointment;
import cn.lcj.rongxiaotong20260918.pojo.entity.Expert;
import cn.lcj.rongxiaotong20260918.pojo.entity.User;
import cn.lcj.rongxiaotong20260918.pojo.vo.AppointmentCreateVO;
import cn.lcj.rongxiaotong20260918.pojo.vo.AppointmentExpertVO;
import cn.lcj.rongxiaotong20260918.pojo.vo.AppointmentVO;
import cn.lcj.rongxiaotong20260918.service.AppointmentService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 专家预约 模块实现
 * 状态机：0待确认 -> 1已确认 -> 2已完成；3已取消（仅待确认可取消）
 * 说明：appointment.expert_id 存专家 user.id（与专家模块一致）
 */
@Service
public class AppointmentServiceImpl implements AppointmentService {

    /** 预约状态 */
    private static final int STATUS_PENDING = 0;  // 待确认
    private static final int STATUS_CONFIRMED = 1; // 已确认
    private static final int STATUS_FINISHED = 2;  // 已完成
    private static final int STATUS_CANCELLED = 3; // 已取消
    /** 默认时长（分钟） */
    private static final int DEFAULT_DURATION = 30;
    /** 专家审核通过 */
    private static final int AUDIT_PASSED = 1;

    @Autowired
    private AppointmentMapper appointmentMapper;
    @Autowired
    private ExpertMapper expertMapper;
    @Autowired
    private UserMapper userMapper;

    @Override
    public AppointmentCreateVO create(Long userId, AppointmentDTO dto) {
        requireLogin(userId);
        if (dto == null) {
            throw new BusinessException(2001, "请求体不能为空");
        }
        if (dto.getExpertId() == null) {
            throw new BusinessException(2001, "请选择专家");
        }
        if (dto.getAppointmentTime() == null) {
            throw new BusinessException(2001, "请选择预约时间");
        }
        // 专家必须存在且审核通过
        Expert expert = expertMapper.selectOne(
                new LambdaQueryWrapper<Expert>().eq(Expert::getUserId, dto.getExpertId()));
        if (expert == null || !Objects.equals(expert.getAuditStatus(), AUDIT_PASSED)) {
            throw new BusinessException(2002, "专家不存在或未通过审核");
        }
        int duration = dto.getDuration() == null || dto.getDuration() <= 0
                ? DEFAULT_DURATION : dto.getDuration();
        // 同一专家同一时间段冲突检测（仅未取消的预约参与）
        checkConflict(dto.getExpertId(), dto.getAppointmentTime(), duration);

        Appointment appointment = new Appointment();
        appointment.setUserId(userId);
        appointment.setExpertId(dto.getExpertId());
        appointment.setAppointmentTime(dto.getAppointmentTime());
        appointment.setDuration(duration);
        appointment.setTopic(StringUtils.hasText(dto.getTopic()) ? dto.getTopic().trim() : null);
        // 锁定专家咨询价
        appointment.setAmount(expert.getConsultPrice());
        appointment.setStatus(STATUS_PENDING);
        appointmentMapper.insert(appointment);

        AppointmentCreateVO vo = new AppointmentCreateVO();
        vo.setId(appointment.getId());
        return vo;
    }

    @Override
    public List<AppointmentVO> myUserList(Long userId, Integer status) {
        requireLogin(userId);
        List<Appointment> list = appointmentMapper.selectList(
                new LambdaQueryWrapper<Appointment>()
                        .eq(Appointment::getUserId, userId)
                        .eq(status != null, Appointment::getStatus, status)
                        .orderByDesc(Appointment::getCreateTime));
        return toVOList(list);
    }

    @Override
    public List<AppointmentVO> myExpertList(Long userId, Integer status) {
        requireLogin(userId);
        mustBeExpert(userId);
        List<Appointment> list = appointmentMapper.selectList(
                new LambdaQueryWrapper<Appointment>()
                        .eq(Appointment::getExpertId, userId)
                        .eq(status != null, Appointment::getStatus, status)
                        .orderByDesc(Appointment::getCreateTime));
        return toVOList(list);
    }

    @Override
    public void confirm(Long userId, Long id) {
        requireLogin(userId);
        Appointment a = mustGet(id);
        checkExpert(a, userId);
        if (a.getStatus() != STATUS_PENDING) {
            throw new BusinessException(2005, "仅待确认预约可确认");
        }
        updateStatus(id, STATUS_CONFIRMED);
    }

    @Override
    public void complete(Long userId, Long id) {
        requireLogin(userId);
        Appointment a = mustGet(id);
        checkExpert(a, userId);
        if (a.getStatus() != STATUS_CONFIRMED) {
            throw new BusinessException(2005, "仅已确认预约可标记完成");
        }
        updateStatus(id, STATUS_FINISHED);
    }

    @Override
    public void cancel(Long userId, Long id) {
        requireLogin(userId);
        Appointment a = mustGet(id);
        // 仅创建者可取消自己的预约
        if (!a.getUserId().equals(userId)) {
            throw new BusinessException(2003, "只能取消自己的预约");
        }
        if (a.getStatus() != STATUS_PENDING) {
            throw new BusinessException(2005, "仅待确认预约可取消");
        }
        updateStatus(id, STATUS_CANCELLED);
    }

    // ---------- 私有辅助方法 ----------

    private void requireLogin(Long userId) {
        if (userId == null) {
            throw new BusinessException(2001, "未登录或登录已过期");
        }
    }

    private void mustBeExpert(Long userId) {
        Long count = expertMapper.selectCount(
                new LambdaQueryWrapper<Expert>().eq(Expert::getUserId, userId));
        if (count == null || count == 0) {
            throw new BusinessException(2003, "当前用户不是专家");
        }
    }

    private Appointment mustGet(Long id) {
        Appointment a = appointmentMapper.selectById(id);
        if (a == null) {
            throw new BusinessException(2002, "预约不存在");
        }
        return a;
    }

    private void checkExpert(Appointment a, Long userId) {
        if (!a.getExpertId().equals(userId)) {
            throw new BusinessException(2003, "只能操作自己名下的预约");
        }
    }

    private void updateStatus(Long id, int status) {
        Appointment update = new Appointment();
        update.setId(id);
        update.setStatus(status);
        appointmentMapper.updateById(update);
    }

    /**
     * 冲突检测：已有预约区间 [start, start+duration) 与新区间重叠则拒绝。
     * 仅待确认(0)、已确认(1) 参与检测；已取消(3)、已完成(2) 不占时段。
     */
    private void checkConflict(Long expertId, LocalDateTime newStart, int newDuration) {
        LocalDateTime newEnd = newStart.plusMinutes(newDuration);
        List<Appointment> existing = appointmentMapper.selectList(
                new LambdaQueryWrapper<Appointment>()
                        .eq(Appointment::getExpertId, expertId)
                        .in(Appointment::getStatus, STATUS_PENDING, STATUS_CONFIRMED));
        for (Appointment a : existing) {
            LocalDateTime aStart = a.getAppointmentTime();
            LocalDateTime aEnd = aStart.plusMinutes(
                    a.getDuration() == null || a.getDuration() <= 0 ? DEFAULT_DURATION : a.getDuration());
            // 重叠：aStart < newEnd 且 aEnd > newStart
            if (aStart.isBefore(newEnd) && aEnd.isAfter(newStart)) {
                throw new BusinessException(2001, "该专家在该时间段已有预约，请选择其他时间");
            }
        }
    }

    private List<AppointmentVO> toVOList(List<Appointment> list) {
        Set<Long> expertUserIds = list.stream()
                .map(Appointment::getExpertId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, User> userMap = expertUserIds.isEmpty() ? Collections.emptyMap()
                : userMapper.selectBatchIds(expertUserIds).stream()
                        .collect(Collectors.toMap(User::getId, Function.identity()));
        Map<Long, Expert> expertMap = expertUserIds.isEmpty() ? Collections.emptyMap()
                : expertMapper.selectList(
                                new LambdaQueryWrapper<Expert>().in(Expert::getUserId, expertUserIds))
                        .stream().collect(Collectors.toMap(Expert::getUserId, Function.identity()));

        return list.stream().map(a -> {
            AppointmentVO vo = new AppointmentVO();
            vo.setId(a.getId());
            vo.setTopic(a.getTopic());
            vo.setAppointmentTime(a.getAppointmentTime());
            vo.setDuration(a.getDuration());
            vo.setAmount(a.getAmount());
            vo.setStatus(a.getStatus());
            vo.setCreateTime(a.getCreateTime());
            User u = userMap.get(a.getExpertId());
            Expert e = expertMap.get(a.getExpertId());
            if (u != null) {
                AppointmentExpertVO expertVO = new AppointmentExpertVO();
                expertVO.setId(u.getId());
                expertVO.setRealName(u.getRealName());
                expertVO.setSpecialty(e == null ? null : e.getSpecialty());
                vo.setExpert(expertVO);
            }
            return vo;
        }).collect(Collectors.toList());
    }
}
