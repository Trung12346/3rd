package su26sd09.su26sd09.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import su26sd09.su26sd09.entity.HoaDon;

public interface HoaDonRepo extends JpaRepository<HoaDon, Integer> {
    HoaDon findByD_Id(Integer maDatPhong);
}
