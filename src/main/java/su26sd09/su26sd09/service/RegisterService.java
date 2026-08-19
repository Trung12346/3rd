package su26sd09.su26sd09.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import su26sd09.su26sd09.dto.RegisterDTO;
import su26sd09.su26sd09.entity.KhachHang;
import su26sd09.su26sd09.entity.VaiTro;
import su26sd09.su26sd09.entity.VerificationToken;
import su26sd09.su26sd09.repository.KhachHangRepository;
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
    private KhachHangRepository nguoiDungRepository;

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

        KhachHang userExisting = nguoiDungRepository.findByEmail(registerDto.getEmail()).orElse(null);

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
        if(registerDto.getMat_khau_hash()==null || registerDto.getMat_khau_hash().length()<7){
            return "password must not null and must have over 7 characters";
        }
        VaiTro vaiTro = vaiTroRepo.findById(3).orElseThrow(()->new RuntimeException("not found"));
        KhachHang nguoiDung = new KhachHang();
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

        // Account is created at this point even if the email fails to send below,
        // so report mail failures distinctly instead of swallowing them as a generic "Error".
        try {
            mailSenderService.EmailSenderVerification(nguoiDung,token);
        } catch (Exception mailEx) {
            mailEx.printStackTrace();
            log.error("Failed to send verification email to {}", nguoiDung.getEmail(), mailEx);
            return "Account created but failed to send verification email. Please try resending it later.";
        }
        System.out.println("check out email");

        return "check our email";
        }catch (Exception e) {
            e.printStackTrace();
            log.error("Cannot Register", e);
            return "Error";
        }
    }
}