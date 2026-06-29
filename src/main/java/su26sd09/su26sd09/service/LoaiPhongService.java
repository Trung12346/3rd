package su26sd09.su26sd09.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import su26sd09.su26sd09.entity.LoaiPhong;
import su26sd09.su26sd09.repository.LoaiPhongRepository;

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
}
