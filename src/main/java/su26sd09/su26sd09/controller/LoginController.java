package su26sd09.su26sd09.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@RequestMapping("")
@Controller
public class LoginController {

    @GetMapping("/login")
    public String Login(Model model){
        model.addAttribute("url", "/login");
        return "login";
    }

    @GetMapping("/nhan-su/login")
    public String login_1(Model model)
    {
        model.addAttribute("url", "/nhan-su/login");
        return "login";
    }

    @GetMapping("/nhan-su")
    public String rd()
    {
        return "redirect:/nhan-su/login";
    }
}
