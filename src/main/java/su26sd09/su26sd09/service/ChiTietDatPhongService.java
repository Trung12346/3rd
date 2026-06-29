package su26sd09.su26sd09.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.stereotype.Service;
import su26sd09.su26sd09.entity.ChiTietDatPhong;
import su26sd09.su26sd09.repository.ChiTietDatPhongRepo;

import java.util.List;

@Service
public class ChiTietDatPhongService {

    @Autowired
    ChiTietDatPhongRepo repo;

    public List<ChiTietDatPhong> findAll(){
        return repo.findAll();
    }

    public void remove(ChiTietDatPhong c){
        repo.delete(c);
    }

    public ChiTietDatPhong findbyId(int id){
        return repo.findById(id).orElse(null);
    }

    public void save(ChiTietDatPhong c){
        repo.save(c);
    }

    public List<ChiTietDatPhong> findByDatPhongId(int id) {
        return repo.findByDatPhongId(id);
    }

    public void deleteByDatPhongId(Integer maDatPhong){
        repo.deleteByDId(maDatPhong);
    }


}
