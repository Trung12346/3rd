package su26sd09.su26sd09.service;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import su26sd09.su26sd09.entity.KhachHang;
import su26sd09.su26sd09.entity.Nhanvien;
import su26sd09.su26sd09.repository.NhanVienRepo;

import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;

@Service
public class NhanVienService {
    @Autowired
    NhanVienRepo repo;
    @Autowired
    UserService NguoiDungRepo;


    public List<Nhanvien> findAll(){


        return repo.findAll();
    }

    public Nhanvien findbyid(int id){
        System.out.println(id);
        return repo.findById(id).orElse(null);

    }
    public void delete(Nhanvien n){
        repo.delete(n);
        System.out.println("sau delete");
        repo.flush();
    }

    public void deletebyid(int id){

        repo.deleteById(id);
    }

    public void save(Nhanvien n){

        repo.save(n);
    }

    public List<Nhanvien> findByName(String name){
        return repo.findbyName(name);
    }



    public Nhanvien findByMaNhanVien(Integer id){
        return repo.findById(id).orElse(null);
    }

    public boolean IsNhanVienTonTai(int id){
        for (Nhanvien nv : repo.findAll()){
            if(nv.getId() == id){
                return true;
            }
        }
        return false;
    }

    public boolean TrungNv(Integer id,int idnv){
        System.out.println(id);
        for (Nhanvien nv : repo.findAll()){
            if ((nv.getId().equals(id) && nv.getId() != idnv)){
                return true;
            }
        }
        return false;
    }

    public void lock(Nhanvien nv) {
     nv.setTrang_thai(false);

    }
    public Nhanvien FindByemail(String email){
    return repo.findByEmail(email).orElse(null);
    }

    public boolean checkTrungCccd(String maCCCD, int id) {
        return repo.existsByMaCCCDAndIdNot(maCCCD, id);
    }

    public Boolean CheckAge(LocalDate ngaysinh){
        int tuoi = Period.between(ngaysinh, LocalDate.now()).getYears();
        if (tuoi >= 18){
            return true;
        }
        return false;
    }
}
