package cn.lcj.rongxiaotong20260918.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LoginController {

    @GetMapping("/login")
    public String login() {
        return "login";
    }


    @GetMapping("/test")
    public String test() {
        return "test";
    }
}
