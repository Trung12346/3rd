package su26sd09.su26sd09.controller;

import org.springframework.stereotype.Controller;
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
    public String registerUser(@ModelAttribute RegisterDTO request) throws Exception {
        registerService.register(request);
        return "redirect:/Login";
    }
    @GetMapping("")
    public String register(){
        return "register";
    }
}