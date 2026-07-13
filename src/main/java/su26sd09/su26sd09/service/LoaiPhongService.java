package su26sd09.su26sd09.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import su26sd09.su26sd09.entity.LoaiPhong;
import su26sd09.su26sd09.repository.LoaiPhongRepository;

import java.math.BigDecimal;
import java.util.List;

@Service
public class LoaiPhongService {

    @Autowired
    LoaiPhongRepository repo;


    public List<LoaiPhong> findAll(){
        return repo.findAll();
    }

    public LoaiPhong findbyid(int id){
        return repo.findById(id).orElse(null);
    }

    public void delete(LoaiPhong p){
        repo.delete(p);
    }

    public void save(LoaiPhong c){
        repo.save(c);
    }

    public List<LoaiPhong> findbyName(String name){
        return repo.findbyName(name);
    }

    public boolean CheckTrungLoai(LoaiPhong l){
        for (LoaiPhong p : findAll()){
            if(p.tenLoai.equals(l.tenLoai) && p.id != l.id ){
                return true;
            }
        }
        return false;
    }

    public List timKiem(String keyword){
        return repo.findbyName(keyword);
    }


    public Page<LoaiPhong> searchPaged(String keyword, BigDecimal minGia, BigDecimal maxGia,
                                       Integer soKhach, Pageable pageable) {
        String kw = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
        return repo.searchLoaiPhongPaged(kw, minGia, maxGia, soKhach, pageable);
    }
}
