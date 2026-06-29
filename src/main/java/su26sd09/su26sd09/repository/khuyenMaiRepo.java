package su26sd09.su26sd09.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import su26sd09.su26sd09.entity.KhuyenMai;



import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.web.bind.annotation.PathVariable;
import su26sd09.su26sd09.entity.KhuyenMai;

import java.util.List;

public interface khuyenMaiRepo extends JpaRepository<KhuyenMai,Integer> {


    @Query("select m from KhuyenMai m where m.promoCode like :name")
    public List<KhuyenMai> findbyPromoCode(@PathVariable("name") String name);
}
