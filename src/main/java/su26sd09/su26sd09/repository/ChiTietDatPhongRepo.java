package su26sd09.su26sd09.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import su26sd09.su26sd09.entity.ChiTietDatPhong;

import java.util.List;

public interface ChiTietDatPhongRepo extends JpaRepository<ChiTietDatPhong,Integer> {
    @Query("select c from ChiTietDatPhong c where c.d.id = :id")
    List<ChiTietDatPhong> findByDatPhongId(@Param("id") int id);

    @Transactional
    void deleteByDId(Integer maDatPhong);

}
