package su26sd09.su26sd09.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import su26sd09.su26sd09.entity.LoaiPhongAnh;
import su26sd09.su26sd09.entity.LoaiPhongAnhId;

import java.util.List;

@Repository
public interface LoaiPhongAnhRepository extends JpaRepository<LoaiPhongAnh, LoaiPhongAnhId> {
    List<LoaiPhongAnh> findByMaLoaiPhong_Id(int maLoaiPhong);

    void deleteByMaLoaiPhong_Id(int maLoaiPhong);

    @Query(value = """
            SELECT TOP 1 * FROM loai_phong_anh WHERE ma_loai_phong = :id
            """, nativeQuery = true) LoaiPhongAnh findByMaLoaiPhongFirst(@Param("id") Integer id);
}
