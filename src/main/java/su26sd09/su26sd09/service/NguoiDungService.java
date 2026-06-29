package su26sd09.su26sd09.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import su26sd09.su26sd09.entity.NguoiDung;
import su26sd09.su26sd09.repository.NguoiDungRepository;

import java.util.List;
import java.util.stream.Stream;

@Service
public class NguoiDungService {

    @Autowired
    NguoiDungRepository nguoiDungRepository;

    public List<NguoiDung> findAll(){
        return nguoiDungRepository.findAll();
    }

    public NguoiDung findById(Integer id){
        return nguoiDungRepository.findById(id).orElse(null);
    }

    public NguoiDung findByName(String name){
        return nguoiDungRepository.findByHoTen(name);
    }

    public NguoiDung findByEmail(String email){
        return nguoiDungRepository.findByEmail(email);
    }

    public Stream<NguoiDung> findWhereRoleNV(){
        return nguoiDungRepository.findAll().stream().filter(nguoiDung -> nguoiDung.getVaiTro().getId() == 2);
    }
}
