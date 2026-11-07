package com.sharath.paycore.gateway.auth;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sharath.paycore.gateway.auth.dto.AuthResponse;
import com.sharath.paycore.gateway.auth.dto.LoginRequest;
import com.sharath.paycore.gateway.auth.dto.RegisterRequest;
import com.sharath.paycore.gateway.security.JwtService;
import com.sharath.paycore.gateway.user.AppUser;
import com.sharath.paycore.gateway.user.UserRepository;

import jakarta.validation.Valid;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Local auth endpoints. This is a WebFlux application with blocking JPA, so
 * every handler wraps its blocking work in Mono.fromCallable and subscribes
 * on Schedulers.boundedElastic().
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthController(UserRepository userRepository,
                          PasswordEncoder passwordEncoder,
                          JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @PostMapping("/register")
    public Mono<ResponseEntity<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) {
        return Mono.fromCallable(() -> {
            if (userRepository.existsByUsername(request.username())) {
                throw new IllegalArgumentException("Username already taken: " + request.username());
            }
            AppUser user = new AppUser(
                    request.username(),
                    passwordEncoder.encode(request.password()),
                    request.fullName());
            userRepository.save(user);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(new AuthResponse(jwtService.generateToken(user.getUsername()), user.getUsername()));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping("/login")
    public Mono<ResponseEntity<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        return Mono.fromCallable(() -> {
            AppUser user = userRepository.findByUsername(request.username())
                    .orElseThrow(() -> new IllegalArgumentException("Invalid username or password"));
            if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
                throw new IllegalArgumentException("Invalid username or password");
            }
            return ResponseEntity.ok(
                    new AuthResponse(jwtService.generateToken(user.getUsername()), user.getUsername()));
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
