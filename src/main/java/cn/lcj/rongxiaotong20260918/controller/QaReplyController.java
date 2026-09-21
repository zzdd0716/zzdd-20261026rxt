package cn.lcj.rongxiaotong20260918.controller;

import cn.lcj.rongxiaotong20260918.service.QaReplyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RestController;
import cn.lcj.rongxiaotong20260918.controller.BaseController;

@RestController
public class QaReplyController extends BaseController {

    @Autowired
    private QaReplyService qaReplyService;
}