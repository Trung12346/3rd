package su26sd09.su26sd09.service;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import su26sd09.su26sd09.entity.NhanSu;
import su26sd09.su26sd09.repository.NhanVienRepo;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.Period;
import java.util.List;

@Service
public class NhanVienService {
    @Autowired
    NhanVienRepo repo;
    @Autowired
    UserService NguoiDungRepo;


    public List<NhanSu> findAll(){


        return repo.findAll();
    }

    public NhanSu findbyid(int id){
        System.out.println(id);
        return repo.findById(id).orElse(null);

    }
    public void delete(NhanSu n){
        repo.delete(n);
        System.out.println("sau delete");
        repo.flush();
    }

    public void deletebyid(int id){

        repo.deleteById(id);
    }

    public void save(NhanSu n){

        repo.save(n);
    }

    public List<NhanSu> findByName(String name){
        return repo.findbyName(name);
    }



    public NhanSu findByMaNhanVien(Integer id){
        return repo.findById(id).orElse(null);
    }

    public boolean IsNhanVienTonTai(int id){
        for (NhanSu nv : repo.findAll()){
            if(nv.getId() == id){
                return true;
            }
        }
        return false;
    }

    public boolean TrungNv(Integer id,int idnv){
        System.out.println(id);
        for (NhanSu nv : repo.findAll()){
            if ((nv.getId().equals(id) && nv.getId() != idnv)){
                return true;
            }
        }
        return false;
    }

    public void lock(NhanSu nv) {
     nv.setTrang_thai(false);

    }
    public NhanSu FindByemail(String email){
    return repo.findByEmail(email).orElse(null);
    }

    public NhanSu findLeTanDangHoatDongMacDinh() {
        return repo.findAll()
                .stream()
                .filter(this::laLeTanDangHoatDong)
                .findFirst()
                .orElse(null);
    }

    public boolean laLeTanDangHoatDong(NhanSu nhanSu) {
        return nhanSu != null
                && nhanSu.isTrang_thai()
                && laBoPhanLeTan(nhanSu.getBoPhan());
    }

    private boolean laBoPhanLeTan(String boPhan) {
        if (boPhan == null) {
            return false;
        }
        String normalized = Normalizer.normalize(boPhan.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return "Lễ Tân".equalsIgnoreCase(boPhan.trim())
                || "Le Tan".equalsIgnoreCase(normalized);
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
