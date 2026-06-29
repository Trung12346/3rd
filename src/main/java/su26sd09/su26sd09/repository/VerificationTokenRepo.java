package su26sd09.su26sd09.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import su26sd09.su26sd09.entity.NguoiDung;
import su26sd09.su26sd09.entity.VerificationToken;

public interface VerificationTokenRepo extends JpaRepository<VerificationToken,Integer> {

    public VerificationToken findByToken(String token);

    void deleteByNguoiDung(NguoiDung nguoiDung);

}
