package cn.lcj.rongxiaotong20260918.pojo.dto;

import lombok.Data;

/**
 * 商品强制上下架请求体
 */
@Data
public class ProductStatusDTO {

    /** 0=下架 1=上架 */
    private Integer status;
}
