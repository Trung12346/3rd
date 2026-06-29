package su26sd09.su26sd09.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import su26sd09.su26sd09.service.VerifyEmailService;

@Controller
public class VerifyEmailController {

    @Autowired
    VerifyEmailService verifyEmailService;

    @GetMapping("/verify-email")
    public String verify(@RequestParam String token ){
        return verifyEmailService.verifyToken(token);
    }
}
