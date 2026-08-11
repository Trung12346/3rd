package su26sd09.su26sd09.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import su26sd09.su26sd09.entity.PhongAnh;
import su26sd09.su26sd09.entity.PhongAnhId;

import java.util.List;

@Repository
public interface PhongAnhRepository extends JpaRepository<PhongAnh, PhongAnhId> {
    List<PhongAnh> findByMaPhong_MaPhong(int maPhong);

    @Query(value = """
SELECT TOP 1 * FROM phong_anh WHERE ma_phong = :maPhong
""", nativeQuery = true)
    PhongAnh findByMaPhongFirst(@Param("maPhong") int maPhong);

    void deleteByMaPhong_MaPhong(int maPhong);
}
