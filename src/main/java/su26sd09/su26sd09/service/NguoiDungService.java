package su26sd09.su26sd09.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import su26sd09.su26sd09.entity.KhachHang;
import su26sd09.su26sd09.repository.KhachHangRepository;

import java.util.List;
import java.util.stream.Stream;

@Service
public class NguoiDungService {

    @Autowired
    KhachHangRepository nguoiDungRepository;

    public KhachHang save(KhachHang n){
        return nguoiDungRepository.save(n);
    }

    public List<KhachHang> findAll(){
        return nguoiDungRepository.findAll();
    }

    public KhachHang findById(Integer id){
        return nguoiDungRepository.findById(id).orElse(null);
    }

    public KhachHang findByName(String name){
        return nguoiDungRepository.findByHoTen(name);
    }

    public KhachHang findByEmail(String email){
        return nguoiDungRepository.findByEmail(email).orElse(null);
    }

    public Stream<KhachHang> findWhereRoleNV(){
        return nguoiDungRepository.findAll().stream().filter(nguoiDung -> nguoiDung.getVaiTro().getId() == 2);
    }
}
