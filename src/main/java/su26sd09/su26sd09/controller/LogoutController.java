package su26sd09.su26sd09.controller;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class LogoutController {

    @GetMapping("/logout")
    public String logout(HttpSession session){
        session.invalidate();
        return "redirect:/login";
    }
    @GetMapping("/nhan-su/logout")
    public String nslogout(HttpSession session){
        session.invalidate();
        return "redirect:/nhan-su/login";
    }
}