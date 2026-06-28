package com.example.upi_tracker.controller;

import com.example.upi_tracker.service.FollowUpEmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/followup")
public class FollowUpController {

    @Autowired
    private FollowUpEmailService followUpEmailService;

    @PostMapping("/trigger")
    public String trigger() {
        return followUpEmailService.triggerManually();
    }
}
