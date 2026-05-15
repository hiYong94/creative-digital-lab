package com.creatived.chat;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController()
public class HealthController {

    @GetMapping("/health-check")
    public String health() {
        return "Hello creative";
    }
}
