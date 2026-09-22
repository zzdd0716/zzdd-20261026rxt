package cn.lcj.rongxiaotong20260918.pojo.vo;

import lombok.Data;

import java.util.List;

/**
 * 某天可预约的时间段（available-time 用）
 */
@Data
public class ExpertDateSlotsVO {

    /** 日期 yyyy-MM-dd */
    private String date;

    /** 可预约时段，如 ["09:00-09:30", "09:30-10:00"] */
    private List<String> slots;
}
