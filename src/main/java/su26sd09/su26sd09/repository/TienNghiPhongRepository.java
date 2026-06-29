package su26sd09.su26sd09.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import su26sd09.su26sd09.entity.TienNghiPhong;
import su26sd09.su26sd09.entity.TienNghiPhongId;

import java.util.List;

public interface TienNghiPhongRepository extends JpaRepository<TienNghiPhong, TienNghiPhongId> {
    List<TienNghiPhong> findByPhongMaPhong(int maPhong);
    @Modifying
    @Transactional
    @Query("DELETE FROM TienNghiPhong t WHERE t.phong.maPhong = :maPhong")

    void deleteByPhongMaPhong(@Param("maPhong") int maPhong);
}
