package com.uptime.monitor.auth;

import com.uptime.monitor.users.AppUserDetailsService;
import com.uptime.monitor.users.AuthUser;
import com.uptime.monitor.users.User;
import com.uptime.monitor.users.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final UserService userService;
    private final LoginThrottle throttle;
    private final SecurityContextRepository contextRepository = new HttpSessionSecurityContextRepository();

    public AuthController(AuthenticationManager authenticationManager, UserService userService, LoginThrottle throttle) {
        this.authenticationManager = authenticationManager;
        this.userService = userService;
        this.throttle = throttle;
    }

    @PostMapping("/signup")
    public ResponseEntity<AuthRequests.UserResponse> signup(@Valid @RequestBody AuthRequests.Signup req,
                                                            HttpServletRequest request, HttpServletResponse response) {
        User user = userService.register(req.email(), req.password());
        AuthUser principal = signIn(user.getEmail(), req.password(), request, response);
        return ResponseEntity.status(HttpStatus.CREATED).body(new AuthRequests.UserResponse(principal.id(), principal.email()));
    }

    @PostMapping("/login")
    public AuthRequests.UserResponse login(@Valid @RequestBody AuthRequests.Login req,
                                           HttpServletRequest request, HttpServletResponse response) {
        String email = AppUserDetailsService.normalize(req.email());
        String ip = request.getRemoteAddr();
        throttle.check(email, ip);
        try {
            AuthUser principal = signIn(email, req.password(), request, response);
            throttle.reset(email);
            return new AuthRequests.UserResponse(principal.id(), principal.email());
        } catch (BadCredentialsException e) {
            throttle.recordFailure(email, ip);
            throw e;
        }
    }

    /**
     * The current user, or 401. Open to everyone so the SPA can ask on startup;
     * reading the CSRF token here also sets the XSRF-TOKEN cookie the SPA sends back.
     */
    @GetMapping("/me")
    public ResponseEntity<AuthRequests.UserResponse> me(@AuthenticationPrincipal AuthUser user, CsrfToken csrf) {
        csrf.getToken();
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(new AuthRequests.UserResponse(user.id(), user.email()));
    }

    private AuthUser signIn(String email, String password, HttpServletRequest request, HttpServletResponse response) {
        Authentication auth = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(email, password));
        request.getSession(true);
        request.changeSessionId(); // new session id on sign-in prevents session fixation
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
        contextRepository.saveContext(context, request, response);
        return (AuthUser) auth.getPrincipal();
    }
}
