package su26sd09.su26sd09.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import su26sd09.su26sd09.entity.PhongAnh;
import su26sd09.su26sd09.entity.PhongAnhId;

import java.util.List;

@Repository
public interface PhongAnhRepository extends JpaRepository<PhongAnh, PhongAnhId> {
    List<PhongAnh> findByMaPhong_MaPhong(int maPhong);

    void deleteByMaPhong_MaPhong(int maPhong);
}
