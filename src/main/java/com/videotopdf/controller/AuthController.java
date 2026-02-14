package com.videotopdf.controller;

import com.videotopdf.model.User;
import com.videotopdf.model.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class AuthController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @GetMapping("/")
    public String home() {
        return "redirect:/dashboard";
    }

    @GetMapping("/login")
    public String loginPage(
            @RequestParam(required = false) String error,
            @RequestParam(required = false) String logout,
            Model model) {
        if (error != null) {
            model.addAttribute("error",
                "Invalid username or password!");
        }
        if (logout != null) {
            model.addAttribute("message",
                "You have been logged out successfully.");
        }
        return "login";
    }

    @GetMapping("/register")
    public String registerPage(Model model) {
        model.addAttribute("user", new User());
        return "register";
    }

    @PostMapping("/register")
    public String registerUser(
            @RequestParam String username,
            @RequestParam String email,
            @RequestParam String password,
            @RequestParam String confirmPassword,
            Model model) {

        // Validate passwords match
        if (!password.equals(confirmPassword)) {
            model.addAttribute("error",
                "Passwords do not match!");
            return "register";
        }

        // Check username exists
        if (userRepository.existsByUsername(username)) {
            model.addAttribute("error",
                "Username already taken!");
            return "register";
        }

        // Check email exists
        if (userRepository.existsByEmail(email)) {
            model.addAttribute("error",
                "Email already registered!");
            return "register";
        }

        // Validate password length
        if (password.length() < 6) {
            model.addAttribute("error",
                "Password must be at least 6 characters!");
            return "register";
        }

        // Save user
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));
        user.setRole("USER");
        userRepository.save(user);

        return "redirect:/login?registered=true";
    }
}
