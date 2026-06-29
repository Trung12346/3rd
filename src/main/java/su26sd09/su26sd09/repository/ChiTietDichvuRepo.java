package su26sd09.su26sd09.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;
import su26sd09.su26sd09.entity.Chi_tiet_dich_vu;

import java.util.List;

public interface ChiTietDichvuRepo extends JpaRepository<Chi_tiet_dich_vu,Integer> {

    public List<Chi_tiet_dich_vu> findByDatPhongId(Integer id);
    @Transactional
     void deleteByDatPhongId(Integer maDatPhong);



}
