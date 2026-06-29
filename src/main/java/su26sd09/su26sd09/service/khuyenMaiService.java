package su26sd09.su26sd09.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import su26sd09.su26sd09.entity.KhuyenMai;
import su26sd09.su26sd09.repository.khuyenMaiRepo;

import java.util.List;
import java.util.stream.Stream;

@Service
public class khuyenMaiService {



    @Autowired
    khuyenMaiRepo repo;


    public List<KhuyenMai> findAll(){
        return repo.findAll();
    }

    public KhuyenMai findbyId(int id){
        return repo.findById(id).orElse(null);

    }
    public void delete(KhuyenMai m){
        repo.delete(m);
    }

    public void save(KhuyenMai m){
        repo.save(m);

    }

    public Stream<KhuyenMai> findAllActive(){
        return  repo.findAll().stream().filter(km -> km.hoatDong == true);
    }

    public List<KhuyenMai> findbyNameVoucher(String name){
        return repo.findbyPromoCode(name);
    }
}