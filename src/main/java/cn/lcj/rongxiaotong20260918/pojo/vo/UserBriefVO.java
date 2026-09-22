package cn.lcj.rongxiaotong20260918.pojo.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * 用户简要信息（通用）
 * - 商品列表/详情：seller（卖家）
 * - 需求列表/详情：buyer（买家）
 * 列表只返回 id/realName/farmName，详情额外返回 username/phone。
 * 空字段不参与序列化，保证与接口文档字段一致。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserBriefVO {

    /** 用户ID */
    private Long id;

    /** 用户名（仅详情返回） */
    private String username;

    /** 真实姓名 */
    private String realName;

    /** 农场/合作社名称 */
    private String farmName;

    /** 手机号（仅详情返回） */
    private String phone;
}
