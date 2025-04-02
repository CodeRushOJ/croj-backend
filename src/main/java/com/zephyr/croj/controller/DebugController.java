package com.zephyr.croj.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/debug")
@Tag(name = "调试", description = "调试接口")
public class DebugController {

    @GetMapping("/auth")
    @Operation(summary = "调试当前用户认证信息")
    public String debugAuth() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return "当前用户: " + auth.getName() + "\n" +
                "权限: " + auth.getAuthorities() + "\n" +
                "是否已认证: " + auth.isAuthenticated() + "\n";
    }
}