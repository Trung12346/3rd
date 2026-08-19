package su26sd09.su26sd09.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import su26sd09.su26sd09.dto.RegisterDTO;
import su26sd09.su26sd09.service.RegisterService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/Register")
public class RegisterController {

    @Autowired
    private RegisterService registerService;

    @PostMapping("")
    public String registerUser(@ModelAttribute RegisterDTO request, Model model) throws Exception {
        String result = registerService.register(request);

        // Only these two outcomes mean the verification email was actually sent.
        boolean success = "check our email".equals(result) || "check out our email".equals(result);

        if (!success) {
            // Something failed (bad password, existing verified email, mail error, etc.)
            // Show it on the register page instead of silently redirecting to Login.
            model.addAttribute("registerError", result);
            model.addAttribute("registerDTO", request);
            return "register";
        }

        model.addAttribute("registerSuccess",
                "Đăng ký thành công! Vui lòng kiểm tra email để xác thực tài khoản.");
        return "register";
    }

    @GetMapping("")
    public String register(){
        return "register";
    }
}