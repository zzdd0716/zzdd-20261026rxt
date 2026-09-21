package cn.lcj.rongxiaotong20260918.common;

import lombok.Getter;

/**
 * 业务异常：携带业务错误码（1001+）
 * 号段分配：A 认证/用户 1001-1999，B 交易 2001-2999，C 知识/融资 3001-3999
 */
@Getter
public class BusinessException extends RuntimeException {

    private final Integer code;

    public BusinessException(Integer code, String message) {
        super(message);
        this.code = code;
    }
}
