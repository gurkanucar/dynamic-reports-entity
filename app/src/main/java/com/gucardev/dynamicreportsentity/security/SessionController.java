package com.gucardev.dynamicreportsentity.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Browser login: the session itself is the resource. {@code POST} logs in (new session id against session
 * fixation), {@code GET} tells the UI who is logged in, {@code DELETE} logs out.
 */
@RestController
@RequestMapping("/api/session")
class SessionController {

    record LoginRequest(@NotBlank @Size(max = 100) String username, @NotBlank @Size(max = 200) String password) {
    }

    record SessionUser(String username, List<String> authorities) {
    }

    private final AuthenticationManager authentication;
    private final SecurityContextRepository contexts;

    SessionController(AuthenticationManager authentication, SecurityContextRepository contexts) {
        this.authentication = authentication;
        this.contexts = contexts;
    }

    @PostMapping
    ResponseEntity<Map<String, SessionUser>> login(@Valid @RequestBody LoginRequest login, HttpServletRequest request,
                                                   HttpServletResponse response) {
        Authentication user = authentication.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(login.username(), login.password()));
        HttpSession previous = request.getSession(false);
        if (previous != null) {
            previous.invalidate();   // a new session id for the new user (session fixation)
        }
        request.getSession(true);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(user);
        SecurityContextHolder.setContext(context);
        contexts.saveContext(context, request, response);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", view(user)));
    }

    @GetMapping
    Map<String, SessionUser> current(Authentication user) {
        return Map.of("data", view(user));
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
    }

    /** Same answer for unknown user and wrong password: nothing tells which one exists. */
    @ExceptionHandler(AuthenticationException.class)
    ProblemDetail invalidCredentials(HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED,
                "Kullanıcı adı veya şifre hatalı.");
        problem.setTitle("Invalid credentials");
        problem.setType(URI.create("/problems/invalid-credentials"));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", "INVALID_CREDENTIALS");
        problem.setProperty("traceId", UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        return problem;
    }

    private static SessionUser view(Authentication user) {
        return new SessionUser(user.getName(), user.getAuthorities().stream().map(GrantedAuthority::getAuthority)
                .filter(authority -> !authority.startsWith("FACTOR_")).sorted()
                .toList());
    }
}
