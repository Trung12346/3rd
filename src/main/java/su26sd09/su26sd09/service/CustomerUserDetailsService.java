package su26sd09.su26sd09.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import su26sd09.su26sd09.entity.NguoiDung;
import su26sd09.su26sd09.entity.UserDetail;
import su26sd09.su26sd09.repository.NguoiDungRepository;

@Service

public class CustomerUserDetailsService  implements UserDetailsService {

    @Autowired
    NguoiDungRepository nguoiDungRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        NguoiDung nguoiDung = nguoiDungRepository.findByEmail(email);
        System.out.println("tim email " + email);
        System.out.println("ket qua" + nguoiDung);
        if(nguoiDung ==null){
            throw new UsernameNotFoundException("Khong tim thay");
        }
        return new UserDetail(nguoiDung);
    }
}
