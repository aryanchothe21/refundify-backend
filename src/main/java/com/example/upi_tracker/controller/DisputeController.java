package com.example.upi_tracker.controller;

import com.example.upi_tracker.entity.User;
import com.example.upi_tracker.service.DisputeService;
import com.example.upi_tracker.service.DashboardService;
import com.example.upi_tracker.service.FollowUpEmailService;
import com.example.upi_tracker.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class DisputeController {

    @Autowired private DisputeService disputeService;
    @Autowired private DashboardService dashboardService;
    @Autowired private UserService userService;

    @PostMapping("/disputes/generate/{transactionId}")
    public ResponseEntity<?> generateDisputeEmail(
            @PathVariable UUID transactionId, Authentication auth) {
        try {
            String email = disputeService.generateDisputeEmail(transactionId, auth.getName());
            return ResponseEntity.ok(Map.of(
                    "message", "Dispute email generated successfully",
                    "status", "DISPUTED",
                    "disputeEmail", email
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/dashboard/me")
    public ResponseEntity<?> getMyDashboard(Authentication auth) {
        User user = userService.findByEmail(auth.getName()).orElseThrow();
        return ResponseEntity.ok(dashboardService.getSummary(user.getId()));
    }
    @Autowired private FollowUpEmailService followUpEmailService;

    @PostMapping("/admin/trigger-followups")
    public ResponseEntity<?> triggerFollowUps(Authentication auth) {
        String result = followUpEmailService.triggerManually();
        return ResponseEntity.ok(Map.of(
                "message", "Follow-up check completed",
                "details", result
        ));
    }}