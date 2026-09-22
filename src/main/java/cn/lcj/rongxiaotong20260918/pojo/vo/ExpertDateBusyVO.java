package cn.lcj.rongxiaotong20260918.pojo.vo;

import lombok.Data;

import java.util.List;

/**
 * 某天被占用的时间段（专家详情 availableTimes 用）
 */
@Data
public class ExpertDateBusyVO {

    /** 日期 yyyy-MM-dd */
    private String date;

    /** 被预约占用的时段，如 ["14:00-14:30"] */
    private List<String> busySlots;
}
