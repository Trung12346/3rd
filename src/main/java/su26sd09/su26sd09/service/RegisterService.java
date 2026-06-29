package su26sd09.su26sd09.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import su26sd09.su26sd09.dto.RegisterDTO;
import su26sd09.su26sd09.entity.NguoiDung;
import su26sd09.su26sd09.entity.VaiTro;
import su26sd09.su26sd09.entity.VerificationToken;
import su26sd09.su26sd09.repository.NguoiDungRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import su26sd09.su26sd09.repository.VaiTroRepo;
import su26sd09.su26sd09.repository.VerificationTokenRepo;

import java.util.UUID;

@Slf4j
@Service
public class RegisterService {

    @Autowired
    private NguoiDungRepository nguoiDungRepository;

    @Autowired
    private VerificationTokenRepo verificationTokenRepo;

    @Autowired
    VaiTroRepo vaiTroRepo;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private MailSenderService mailSenderService;

    @Transactional
    public String register(RegisterDTO registerDto) throws Exception {
        try {

        NguoiDung userExisting = nguoiDungRepository.findByEmail(registerDto.getEmail());

        if(userExisting!=null){
            if(userExisting.isTrangThai()){
                return "Email Already Exist";
            }else{
                verificationTokenRepo.deleteByNguoiDung(userExisting);

                String token = UUID.randomUUID().toString();
                verificationTokenRepo.save(new VerificationToken(token,userExisting));
                mailSenderService.EmailSenderVerification(userExisting,token);
                System.out.println("resend email success");
                return "check out our email";
            }
        }
        if(registerDto.getMat_khau_hash()==null&&registerDto.getMat_khau_hash().length()<7){
            return "password must not null and must have over 7 characters";
        }
        VaiTro vaiTro = vaiTroRepo.findById(3).orElseThrow(()->new RuntimeException("not found"));
        NguoiDung nguoiDung = new NguoiDung();
        nguoiDung.setHoTen(registerDto.getHo_ten());
        nguoiDung.setEmail(registerDto.getEmail());
        nguoiDung.setMatKhau_hash(passwordEncoder.encode(registerDto.getMat_khau_hash()));
        nguoiDung.setSoDienThoai(registerDto.getSo_dien_thoai());
        nguoiDung.setTrangThai(false);
        nguoiDung.setDiaChi(registerDto.getDia_chi());
        nguoiDung.setVaiTro(vaiTro);
        nguoiDungRepository.save(nguoiDung);
        String token =UUID.randomUUID().toString();
        verificationTokenRepo.save(new VerificationToken(token,nguoiDung));
        mailSenderService.EmailSenderVerification(nguoiDung,token);
        System.out.println("check out email");

        return "check our email";
        }catch (Exception e) {
            e.printStackTrace();
            System.out.println("Cannot Register : " + e.getMessage());
            return "Error";
        }
    }
}