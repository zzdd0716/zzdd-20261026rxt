package cn.lcj.rongxiaotong20260918.common;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 统一分页返回结构（字段名与接口文档一致：list / total / pageNum / pageSize / pages）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> {

    /** 当前页数据 */
    private List<T> list;
    /** 总记录数 */
    private Long total;
    /** 当前页码（从 1 开始） */
    private Integer pageNum;
    /** 每页条数 */
    private Integer pageSize;
    /** 总页数 */
    private Integer pages;

    /** 用 MyBatis-Plus 分页结果构造（数据行自定义覆盖） */
    public static <T, E> PageResult<T> of(Page<E> page, List<T> list) {
        return new PageResult<>(list, page.getTotal(),
                (int) page.getCurrent(), (int) page.getSize(), (int) page.getPages());
    }
}
