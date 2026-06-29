package su26sd09.su26sd09.service;

import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import su26sd09.su26sd09.entity.NguoiDung;
import su26sd09.su26sd09.entity.Nhanvien;
import su26sd09.su26sd09.repository.NhanVienRepo;

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
        if (n.n.getVaiTro().getTenVaiTro().equals("ROLE_ADMIN") || n.n.getVaiTro().getTenVaiTro().equals("ROLE_EMPLOYEE")){
            NguoiDungRepo.save(n.n);
            return ;
        }

        repo.save(n);
    }

    public List<Nhanvien> findByName(String name){
        return repo.findbyName(name);
    }

    public List<Nhanvien> ListAdd(){
        List<Nhanvien> Listnv = new ArrayList<>();
        for (NguoiDung n : NguoiDungRepo.getAll()){
            if(n.getVaiTro().getTenVaiTro().equals("ROLE_STAFF") ){
                Nhanvien nv = new Nhanvien();
                nv.setN(n);
                Listnv.add(nv);
            }
        }
        return Listnv;
    }

    public Nhanvien findByMaNguoiDung(Integer id){
        return repo.findByN_MaNguoiDung(id);
    }

    public boolean IsNhanVienTonTai(int id){
        for (Nhanvien nv : repo.findAll()){
            if(nv.n.getMaNguoiDung() == id){
                return true;
            }
        }
        return false;
    }

    public boolean TrungNv(Integer id,int idnv){
        System.out.println(id);
        for (Nhanvien nv : repo.findAll()){
            if ((nv.n.getMaNguoiDung().equals(id) && nv.getId() != idnv)){
                return true;
            }
        }
        return false;
    }

    public void lock(Nhanvien nv) {
     nv.n.setTrangThai(false);

    }
}
