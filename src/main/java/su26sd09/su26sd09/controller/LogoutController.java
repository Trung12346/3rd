package su26sd09.su26sd09.controller;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@RequestMapping("/logout")
@Controller
public class LogoutController {

    @GetMapping("")
    public String logout(HttpSession session){
        session.invalidate();
        return "redirect:/Login";
    }
}