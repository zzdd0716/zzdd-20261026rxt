package cn.lcj.rongxiaotong20260918.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Accessors(chain = true)
public class Result<T> {
    private Integer code;
    private String msg;
    private T data;

    // 成功
    public static <T> Result<T> success(T data) {
        return new Result<>(200, "操作成功", data);
    }
    // 成功
    public static <T> Result<T> success() {
        return new Result<>(200, "操作成功", null);
    }
    // 成功
    public static <T> Result<T> success(String msg) {
        return new Result<>(200, msg, null);
    }
    // 失败
    public static <T> Result<T> error(Integer code, String msg) {
        return new Result<>(code, msg, null);
    }
}
