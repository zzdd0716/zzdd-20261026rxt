package cn.lcj.rongxiaotong20260918.pojo.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("qa_reply")
public class QaReply {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long qaId;
    private Long replierId;
    private String content;
    private Integer isExpert;
    private LocalDateTime createTime;
}