package cn.lcj.rongxiaotong20260918.pojo.dto;

import lombok.Data;

import java.util.List;

/**
 * 购物车批量勾选请求体
 */
@Data
public class CartSelectDTO {

    /** 购物车项ID数组 */
    private List<Long> ids;

    /** 0=取消 1=勾选 */
    private Integer selected;
}
