package cn.lcj.rongxiaotong20260918.controller;

import cn.lcj.rongxiaotong20260918.service.KnowledgeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RestController;
import cn.lcj.rongxiaotong20260918.controller.BaseController;

@RestController
public class KnowledgeController extends BaseController {

    @Autowired
    private KnowledgeService knowledgeService;
}