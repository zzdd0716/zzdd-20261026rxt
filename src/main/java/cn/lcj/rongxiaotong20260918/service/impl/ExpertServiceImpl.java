package cn.lcj.rongxiaotong20260918.service.impl;

import cn.lcj.rongxiaotong20260918.common.BusinessException;
import cn.lcj.rongxiaotong20260918.common.PageResult;
import cn.lcj.rongxiaotong20260918.mapper.AppointmentMapper;
import cn.lcj.rongxiaotong20260918.mapper.ExpertMapper;
import cn.lcj.rongxiaotong20260918.mapper.UserMapper;
import cn.lcj.rongxiaotong20260918.pojo.dto.ExpertProfileDTO;
import cn.lcj.rongxiaotong20260918.pojo.entity.Appointment;
import cn.lcj.rongxiaotong20260918.pojo.entity.Expert;
import cn.lcj.rongxiaotong20260918.pojo.entity.User;
import cn.lcj.rongxiaotong20260918.pojo.vo.ExpertDateBusyVO;
import cn.lcj.rongxiaotong20260918.pojo.vo.ExpertDateSlotsVO;
import cn.lcj.rongxiaotong20260918.pojo.vo.ExpertDetailVO;
import cn.lcj.rongxiaotong20260918.pojo.vo.ExpertListVO;
import cn.lcj.rongxiaotong20260918.pojo.vo.ExpertProfileVO;
import cn.lcj.rongxiaotong20260918.service.ExpertService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 专家 模块实现
 * 说明：对外"专家ID"统一为 user.id（与 appointment.expert_id 一致），
 * expert 表主键 id 仅作内部关联。
 */
@Service
public class ExpertServiceImpl implements ExpertService {

    /** 审核通过 */
    private static final int AUDIT_PASSED = 1;
    /** 预约未取消状态：0-待确认 1-已确认 */
    private static final List<Integer> ACTIVE_APPOINTMENT_STATUS = List.of(0, 1);

    /** 每天可约时段：09:00-18:00，30 分钟一段 */
    private static final LocalTime WORK_START = LocalTime.of(9, 0);
    private static final LocalTime WORK_END = LocalTime.of(18, 0);
    private static final int SLOT_MINUTES = 30;
    /** 可约天数（未来 14 天，从明天起） */
    private static final int AVAILABLE_DAYS = 14;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    @Autowired
    private ExpertMapper expertMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private AppointmentMapper appointmentMapper;

    @Override
    public PageResult<ExpertListVO> list(String keyword, Integer pageNum, Integer pageSize) {
        int num = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int size = pageSize == null || pageSize < 1 ? 10 : pageSize;

        LambdaQueryWrapper<Expert> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Expert::getAuditStatus, AUDIT_PASSED);
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            // 姓名在 user 表，专长/职称在 expert 表
            List<Long> nameUserIds = userMapper.selectList(
                            new LambdaQueryWrapper<User>().like(User::getRealName, kw))
                    .stream().map(User::getId).collect(Collectors.toList());
            wrapper.and(w -> {
                w.like(Expert::getSpecialty, kw).or().like(Expert::getTitle, kw);
                if (!nameUserIds.isEmpty()) {
                    w.or().in(Expert::getUserId, nameUserIds);
                }
            });
        }
        wrapper.orderByDesc(Expert::getConsultCount);
        Page<Expert> page = expertMapper.selectPage(new Page<>(num, size), wrapper);

        Map<Long, User> userMap = userMap(page.getRecords().stream()
                .map(Expert::getUserId).collect(Collectors.toList()));
        List<ExpertListVO> list = page.getRecords().stream()
                .map(e -> toListVO(e, userMap.get(e.getUserId())))
                .collect(Collectors.toList());
        return PageResult.of(page, list);
    }

    @Override
    public ExpertDetailVO detail(Long expertId) {
        Expert e = mustGetByUserId(expertId);
        User u = userMapper.selectById(e.getUserId());

        ExpertDetailVO vo = new ExpertDetailVO();
        vo.setId(e.getUserId());
        vo.setUserId(e.getUserId());
        vo.setRealName(u == null ? null : u.getRealName());
        vo.setAvatar(u == null ? null : u.getAvatar());
        vo.setPhone(u == null ? null : u.getPhone());
        vo.setSpecialty(e.getSpecialty());
        vo.setTitle(e.getTitle());
        vo.setIntroduction(e.getIntroduction());
        vo.setConsultPrice(e.getConsultPrice());
        vo.setRating(e.getRating());
        vo.setConsultCount(e.getConsultCount());
        vo.setAvailableTimes(busySlotsOfNext14Days(e.getUserId()));
        return vo;
    }

    @Override
    public ExpertProfileVO profile(Long userId) {
        requireLogin(userId);
        Expert e = mustGetByUserId(userId);
        ExpertProfileVO vo = new ExpertProfileVO();
        vo.setSpecialty(e.getSpecialty());
        vo.setTitle(e.getTitle());
        vo.setIntroduction(e.getIntroduction());
        vo.setConsultPrice(e.getConsultPrice());
        vo.setRating(e.getRating());
        vo.setConsultCount(e.getConsultCount());
        vo.setAuditStatus(e.getAuditStatus());
        vo.setAuditRemark(e.getAuditRemark());
        return vo;
    }

    @Override
    public void updateProfile(Long userId, ExpertProfileDTO dto) {
        requireLogin(userId);
        if (dto == null) {
            throw new BusinessException(2001, "请求体不能为空");
        }
        if (!StringUtils.hasText(dto.getSpecialty())) {
            throw new BusinessException(2001, "专长领域不能为空");
        }
        Expert e = mustGetByUserId(userId);

        Expert update = new Expert();
        update.setId(e.getId());
        update.setSpecialty(dto.getSpecialty().trim());
        update.setTitle(StringUtils.hasText(dto.getTitle()) ? dto.getTitle().trim() : null);
        update.setIntroduction(StringUtils.hasText(dto.getIntroduction()) ? dto.getIntroduction().trim() : null);
        // 文档：咨询价格必填，默认 0
        update.setConsultPrice(dto.getConsultPrice() == null ? BigDecimal.ZERO : dto.getConsultPrice());
        expertMapper.updateById(update);
    }

    @Override
    public List<ExpertDateSlotsVO> availableTime(Long expertId) {
        mustGetByUserId(expertId);
        Map<LocalDate, Set<String>> occupied = occupiedSlots(expertId);

        List<ExpertDateSlotsVO> result = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (int i = 1; i <= AVAILABLE_DAYS; i++) {
            LocalDate date = today.plusDays(i);
            List<String> slots = genDaySlots();
            Set<String> busy = occupied.getOrDefault(date, Collections.emptySet());
            slots.removeAll(busy);
            if (!slots.isEmpty()) {
                ExpertDateSlotsVO vo = new ExpertDateSlotsVO();
                vo.setDate(date.format(DATE_FMT));
                vo.setSlots(slots);
                result.add(vo);
            }
        }
        return result;
    }

    // ---------- 私有辅助方法 ----------

    private void requireLogin(Long userId) {
        if (userId == null) {
            throw new BusinessException(2001, "未登录或登录已过期");
        }
    }

    /** 按 user.id 定位专家记录 */
    private Expert mustGetByUserId(Long userId) {
        if (userId == null) {
            throw new BusinessException(2001, "参数错误");
        }
        Expert e = expertMapper.selectOne(
                new LambdaQueryWrapper<Expert>().eq(Expert::getUserId, userId));
        if (e == null) {
            throw new BusinessException(2002, "专家不存在");
        }
        return e;
    }

    private ExpertListVO toListVO(Expert e, User u) {
        ExpertListVO vo = new ExpertListVO();
        vo.setId(e.getUserId());
        vo.setUserId(e.getUserId());
        vo.setRealName(u == null ? null : u.getRealName());
        vo.setSpecialty(e.getSpecialty());
        vo.setTitle(e.getTitle());
        vo.setRating(e.getRating());
        vo.setConsultCount(e.getConsultCount());
        vo.setConsultPrice(e.getConsultPrice());
        vo.setAvatar(u == null ? null : u.getAvatar());
        return vo;
    }

    private Map<Long, User> userMap(List<Long> userIds) {
        List<Long> ids = userIds.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return userMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
    }

    /** 生成一天全部时段：09:00-18:00 每 30 分钟一段 */
    private List<String> genDaySlots() {
        List<String> slots = new ArrayList<>();
        LocalTime t = WORK_START;
        while (t.isBefore(WORK_END)) {
            slots.add(t.format(TIME_FMT) + "-" + t.plusMinutes(SLOT_MINUTES).format(TIME_FMT));
            t = t.plusMinutes(SLOT_MINUTES);
        }
        return slots;
    }

    /** 查询未来14天内未取消的预约，返回 日期 -> 被占时段 集合 */
    private Map<LocalDate, Set<String>> occupiedSlots(Long expertId) {
        LocalDate today = LocalDate.now();
        LocalDateTime start = today.plusDays(1).atStartOfDay();
        LocalDateTime end = today.plusDays(AVAILABLE_DAYS + 1L).atStartOfDay();

        List<Appointment> apps = appointmentMapper.selectList(
                new LambdaQueryWrapper<Appointment>()
                        .eq(Appointment::getExpertId, expertId)
                        .in(Appointment::getStatus, ACTIVE_APPOINTMENT_STATUS)
                        .ge(Appointment::getAppointmentTime, start)
                        .lt(Appointment::getAppointmentTime, end));

        Map<LocalDate, Set<String>> map = new HashMap<>();
        for (Appointment app : apps) {
            LocalDate date = app.getAppointmentTime().toLocalDate();
            Set<String> busy = map.computeIfAbsent(date, k -> new HashSet<>());
            LocalTime s = app.getAppointmentTime().toLocalTime();
            LocalTime slotEnd = s.plusMinutes(
                    app.getDuration() == null || app.getDuration() <= 0 ? SLOT_MINUTES : app.getDuration());
            // 预约覆盖的每个 30 分钟时段都视为占用（预约起点对齐到整点/半点，由创建预约接口保证）
            LocalTime cur = s;
            while (cur.isBefore(slotEnd)) {
                busy.add(cur.format(TIME_FMT) + "-" + cur.plusMinutes(SLOT_MINUTES).format(TIME_FMT));
                cur = cur.plusMinutes(SLOT_MINUTES);
            }
        }
        return map;
    }

    /** 未来14天每天被占时段（专家详情 availableTimes 用，全部日期返回） */
    private List<ExpertDateBusyVO> busySlotsOfNext14Days(Long expertId) {
        Map<LocalDate, Set<String>> occupied = occupiedSlots(expertId);
        List<ExpertDateBusyVO> result = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (int i = 1; i <= AVAILABLE_DAYS; i++) {
            LocalDate date = today.plusDays(i);
            ExpertDateBusyVO vo = new ExpertDateBusyVO();
            vo.setDate(date.format(DATE_FMT));
            vo.setBusySlots(new ArrayList<>(occupied.getOrDefault(date, Collections.emptySet())));
            result.add(vo);
        }
        return result;
    }
}
