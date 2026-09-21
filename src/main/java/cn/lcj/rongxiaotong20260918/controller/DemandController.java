package cn.lcj.rongxiaotong20260918.controller;

import cn.lcj.rongxiaotong20260918.service.DemandService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RestController;
import cn.lcj.rongxiaotong20260918.controller.BaseController;

@RestController
public class DemandController extends BaseController {

    @Autowired
    private DemandService demandService;
}