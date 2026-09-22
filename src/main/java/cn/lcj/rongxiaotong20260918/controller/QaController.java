package cn.lcj.rongxiaotong20260918.controller;

import cn.lcj.rongxiaotong20260918.common.PageResult;
import cn.lcj.rongxiaotong20260918.common.Result;
import cn.lcj.rongxiaotong20260918.pojo.dto.QaDTO;
import cn.lcj.rongxiaotong20260918.pojo.dto.QaReplyDTO;
import cn.lcj.rongxiaotong20260918.pojo.vo.QaCreateVO;
import cn.lcj.rongxiaotong20260918.pojo.vo.QaDetailVO;
import cn.lcj.rongxiaotong20260918.pojo.vo.QaListVO;
import cn.lcj.rongxiaotong20260918.pojo.vo.QaMyQuestionVO;
import cn.lcj.rongxiaotong20260918.pojo.vo.QaMyReplyVO;
import cn.lcj.rongxiaotong20260918.service.QaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 在线问答模块（后端 B）
 * 接口文档：02-backend-B-trade-expert.md 第六章
 *
 * 6.1/6.2 公开；6.3-6.9 需登录。
 * 注意路由：/api/qa/my/question、/api/qa/my/reply、/api/qa/reply/{id}
 * 与 /api/qa/{id} 精确匹配优先，Spring 字面量路由优先于路径变量。
 */
@RestController
@RequestMapping("/api/qa")
public class QaController extends BaseController {

    @Autowired
    private QaService qaService;

    /** 6.1 问答列表（公开） */
    @GetMapping
    public Result<PageResult<QaListVO>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false, defaultValue = "1") Integer pageNum,
            @RequestParam(required = false, defaultValue = "10") Integer pageSize) {
        return Result.success(qaService.list(keyword, categoryId, status, pageNum, pageSize));
    }

    /** 6.2 问答详情（公开，浏览数+1） */
    @GetMapping("/{id}")
    public Result<QaDetailVO> detail(@PathVariable Long id) {
        return Result.success(qaService.detail(id));
    }

    /** 6.3 发起提问（需登录） */
    @PostMapping
    public Result<QaCreateVO> create(@RequestBody QaDTO dto) {
        return Result.success(qaService.create(getCurrentUserId(), dto));
    }

    /** 6.4 修改自己的问题（需登录） */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody QaDTO dto) {
        qaService.update(getCurrentUserId(), id, dto);
        return Result.success();
    }

    /** 6.5 删除自己的问题（需登录） */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        qaService.delete(getCurrentUserId(), id);
        return Result.success();
    }

    /** 6.6 回答问题（需登录） */
    @PostMapping("/{id}/reply")
    public Result<Void> reply(@PathVariable Long id, @RequestBody QaReplyDTO dto) {
        qaService.reply(getCurrentUserId(), id, dto);
        return Result.success();
    }

    /** 6.7 我的提问（需登录） */
    @GetMapping("/my/question")
    public Result<List<QaMyQuestionVO>> myQuestion() {
        return Result.success(qaService.myQuestion(getCurrentUserId()));
    }

    /** 6.8 我的回答（需登录） */
    @GetMapping("/my/reply")
    public Result<List<QaMyReplyVO>> myReply() {
        return Result.success(qaService.myReply(getCurrentUserId()));
    }

    /** 6.9 删除自己的回答（需登录） */
    @DeleteMapping("/reply/{id}")
    public Result<Void> deleteReply(@PathVariable Long id) {
        qaService.deleteReply(getCurrentUserId(), id);
        return Result.success();
    }
}
