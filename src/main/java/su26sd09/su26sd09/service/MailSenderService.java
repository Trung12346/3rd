package su26sd09.su26sd09.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailSender;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.stereotype.Service;
import su26sd09.su26sd09.entity.NguoiDung;

@Service
@RequiredArgsConstructor
public class MailSenderService {
    private final MailSender mailSender;

    @Value("${app.base-url}")
    private String baseurl;

    public void EmailSenderVerification(NguoiDung nguoiDung,String token) throws Exception {
    String verifyLink = "";
    if(!nguoiDung.getEmail().matches("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$")){
        System.out.println("email not valid");
        throw new Exception("Email not valid");
    }else{
        verifyLink = baseurl + "/verify-email?token="+token;
        SimpleMailMessage smp =new SimpleMailMessage();
        smp.setTo("day la xac thuc email : ");
        smp.setTo(nguoiDung.getEmail());
        smp.setSubject("Authentication Account");
        smp.setText(nguoiDung.getEmail() + "click this link to verify email "+verifyLink+ "");
        mailSender.send(smp);
    }
    }
}
