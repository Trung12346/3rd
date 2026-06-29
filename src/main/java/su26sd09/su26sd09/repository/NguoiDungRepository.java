package su26sd09.su26sd09.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import su26sd09.su26sd09.entity.NguoiDung;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NguoiDungRepository extends JpaRepository<NguoiDung, Integer> {
    boolean existsByEmail(String email);
    boolean existsBySoDienThoai(String soDienThoai);
    public NguoiDung findByEmail(String email);
    NguoiDung findBySoDienThoai(String soDienThoai);


    @Query("select n from NguoiDung n where n.hoTen like concat('%',:name,'%')")
    public List<NguoiDung> search(@Param("name") String name);

    @Query(value = "SELECT * FROM nguoi_dung WHERE LOWER(ho_ten) LIKE CONCAT('%', LOWER(:ten), '%') AND ma_vai_tro = 3", nativeQuery = true)
    public List<NguoiDung> findAllKhach(@Param("ten") String ten);

    @Query(value = "SELECT * FROM nguoi_dung WHERE ma_nguoi_dung <> :id", nativeQuery = true)
    public List<NguoiDung> findOthers(@Param("id") Integer id);

    public NguoiDung findByHoTen(String name);

}
