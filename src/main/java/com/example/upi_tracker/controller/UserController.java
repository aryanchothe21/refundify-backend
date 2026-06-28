package com.example.upi_tracker.controller;

import com.example.upi_tracker.config.JwtUtil;
import com.example.upi_tracker.dto.LoginRequest;
import com.example.upi_tracker.dto.RegisterRequest;
import com.example.upi_tracker.entity.User;
import com.example.upi_tracker.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/users")
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private JwtUtil jwtUtil;

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
        try {
            User user = userService.register(request);

            return ResponseEntity.ok(Map.of(
                    "message", "User registered successfully",
                    "userId", user.getId(),
                    "name", user.getName(),
                    "email", user.getEmail()
            ));

        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(
                    Map.of("error", e.getMessage())
            );
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        try {
            User user = userService.login(
                    request.getEmail(),
                    request.getPassword()
            );

            String token = jwtUtil.generateToken(user.getEmail());

            return ResponseEntity.ok(Map.of(
                    "token", token,
                    "userId", user.getId(),
                    "name", user.getName(),
                    "email", user.getEmail()
            ));

        } catch (RuntimeException e) {
            return ResponseEntity.status(401)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/by-email/{email}")
    @Operation(summary = "Find user by email")
    public ResponseEntity<?> getByEmail(@PathVariable String email) {
        return userService.findByEmail(email)
                .map(u -> ResponseEntity.ok(Map.of(
                        "userId", u.getId(),
                        "name", u.getName(),
                        "email", u.getEmail()
                )))
                .orElse(ResponseEntity.status(404)
                        .body(Map.of("error", "No account found")));
    }
}
