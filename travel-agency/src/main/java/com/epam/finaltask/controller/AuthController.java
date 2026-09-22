package com.epam.finaltask.controller;

import com.epam.finaltask.dto.UserDTO;
import com.epam.finaltask.exception.ExistingUserException;
import com.epam.finaltask.exception.InvalidCredentialsException;
import com.epam.finaltask.exception.RepeatAuthenticationException;
import com.epam.finaltask.model.Role;
import com.epam.finaltask.request.SignInRequest;
import com.epam.finaltask.request.SignUpRequest;
import com.epam.finaltask.service.RefreshTokenService;
import com.epam.finaltask.service.UserService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriComponentsBuilder;

import java.security.Principal;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Controller
@RequiredArgsConstructor
public class AuthController {

    private final RefreshTokenService refreshTokenService;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;

    @GetMapping("/sign-in")
    public String signIn(
            @RequestParam(name = "redirect", required = false) String redirect,
            @RequestParam(name = "message", required = false) String message,
            Model model,
            HttpServletRequest request
    ) {
        if (isAuthenticated()) {
            throw new RepeatAuthenticationException("You've been signed in already. Logout first");
        }
        model.addAttribute("message", message);
        model.addAttribute("redirect", redirect);
        model.addAttribute("signInRequest", new SignInRequest());
        return "/auth/sign-in";
    }

    @GetMapping("/sign-up")
    public String signUp(Model model) {
        if (isAuthenticated()) {
            throw new RepeatAuthenticationException("You've been signed in already. Logout first");
        }
        model.addAttribute("signUpRequest", new SignUpRequest());
        return "/auth/sign-up";
    }

    @PostMapping("/sign-up")
    public String signUp(
            @Valid @ModelAttribute("signUpRequest") SignUpRequest signUpRequest,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            return "/auth/sign-up";
        }
        if (userService.existsByUsernameOrEmail(signUpRequest.getUsername(), signUpRequest.getEmail())) {
            throw new ExistingUserException("User already exists with given username or email");
        }
        userService.register(new UserDTO(
                null, signUpRequest.getUsername(), passwordEncoder.encode(signUpRequest.getPassword()),
                Role.USER.name(), List.of(), signUpRequest.getEmail(), signUpRequest.getPhoneNumber(), (double) 0,true
        ));
        redirectAttributes.addAttribute("message", "Registered successfully!");
        return "redirect:/sign-in";
    }

    private void removeTokenCookies(String username, HttpServletResponse response) {
        refreshTokenService.deleteRefreshToken(username);

        setCookieToResponse(
                "Authentication",
                null,
                "/",
                0,
                response
        );

        setCookieToResponse(
                "Refresh-Token",
                null,
                "/",
                0,
                response
        );
    }

    private void setCookieToResponse(String name, String value, String path, int maxAge, HttpServletResponse response) {
        Cookie tokenCookie = new Cookie(name, value);
        tokenCookie.setHttpOnly(true);
        tokenCookie.setSecure(true);
        tokenCookie.setPath(path);
        tokenCookie.setMaxAge(maxAge);
        response.addCookie(tokenCookie);
    }

    private boolean isAuthenticated() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.isAuthenticated() && !(authentication instanceof AnonymousAuthenticationToken);
    }

}
