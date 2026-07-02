package su26sd09.su26sd09.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import su26sd09.su26sd09.entity.Dich_vu;

import java.util.List;

public interface DichVuRepo extends JpaRepository<Dich_vu,Integer> {

    @Query("SELECT d FROM Dich_vu d " +
            "WHERE (:keyword IS NULL OR :keyword = '' OR LOWER(d.ten_dich_vu) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
            "AND (:trangThai IS NULL OR :trangThai = '' " +
            "     OR (:trangThai = 'active' AND d.hoat_dong = true) " +
            "     OR (:trangThai = 'inactive' AND (d.hoat_dong = false OR d.hoat_dong IS NULL))) " +
            "ORDER BY d.id DESC")
    List<Dich_vu> search(@Param("keyword") String keyword, @Param("trangThai") String trangThai);

    @Query("SELECT COUNT(d) FROM Dich_vu d WHERE d.hoat_dong = true")
    long countActive();
}
