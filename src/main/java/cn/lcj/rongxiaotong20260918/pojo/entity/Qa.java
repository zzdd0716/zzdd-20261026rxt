package cn.lcj.rongxiaotong20260918.pojo.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("qa")
public class Qa {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long questionerId;
    private Long expertId;
    private Long categoryId;
    private String title;
    private String content;
    private String images;
    private Integer status;
    private Integer viewCount;
    private Integer replyCount;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}