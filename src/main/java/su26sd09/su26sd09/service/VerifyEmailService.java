package su26sd09.su26sd09.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import su26sd09.su26sd09.entity.NguoiDung;
import su26sd09.su26sd09.entity.VaiTro;
import su26sd09.su26sd09.entity.VerificationToken;
import su26sd09.su26sd09.repository.NguoiDungRepository;
import su26sd09.su26sd09.repository.VaiTroRepo;
import su26sd09.su26sd09.repository.VerificationTokenRepo;

@Service
public class VerifyEmailService {

    @Autowired
    VerificationTokenRepo tokenRepo;

    @Autowired
    NguoiDungRepository nguoiDungRepository;

    @Autowired
    VaiTroRepo vaiTroRepo;

    public String verifyToken(String token){
        VerificationToken verificationToken = tokenRepo.findByToken(token);
        System.out.println("Token: " + token);
        System.out.println("Found: " + verificationToken);


        if(verificationToken == null){
            return "invalid token";
        }
        System.out.println("Expired: " + verificationToken.isExpired());
        System.out.println("Used: " + verificationToken.getUsed());
        if(verificationToken.isExpired()){
            return "Token is expired";
        }
        if(Boolean.TRUE.equals(verificationToken.getUsed())){
            return "Token is Already Used";
        }
        VaiTro vaiTro = vaiTroRepo.findById(3).
                orElseThrow(() ->new RuntimeException("not found"));
        NguoiDung nguoiDung = verificationToken.getNguoiDung();
        nguoiDung.setTrangThai(true);
        nguoiDung.setVaiTro(vaiTro);
        nguoiDungRepository.save(nguoiDung);
        verificationToken.setUsed(true);
        tokenRepo.save(verificationToken);
        System.out.println("xac thuc token thanh cong ");
        return "redirect:/Login";
    }
}
