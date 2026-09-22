package cn.lcj.rongxiaotong20260918.pojo.dto;

import lombok.Data;

/**
 * 修改购物车数量请求体
 */
@Data
public class CartQuantityDTO {

    /** 新数量 */
    private Integer quantity;
}
