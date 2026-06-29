package su26sd09.su26sd09.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@RequestMapping("/Login")
@Controller
public class LoginController {

    @GetMapping("")
    public String Login(){
        return "login";
    }
}
