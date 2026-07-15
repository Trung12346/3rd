package su26sd09.su26sd09.repository;


import org.springframework.data.jpa.repository.JpaRepository;
import su26sd09.su26sd09.entity.ThanhToan;

import java.util.List;

public interface ThanhToanRepo extends JpaRepository<ThanhToan,Integer> {
        List<ThanhToan> findByH_IdOrderByNgaythanhToanAsc(int maHoaDon);
        ThanhToan findByH_Id(int maHoaDon);
}
