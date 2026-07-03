package su26sd09.su26sd09.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import su26sd09.su26sd09.entity.KhachHang;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface KhachHangRepository extends JpaRepository<KhachHang, Integer> {
    boolean existsByEmail(String email);
    boolean existsBySoDienThoai(String soDienThoai);
    public Optional<KhachHang> findByEmail(String email);
    KhachHang findBySoDienThoai(String soDienThoai);


    @Query("select n from KhachHang n where n.hoTen like concat('%',:name,'%')")
    public List<KhachHang> search(@Param("name") String name);

    @Query(value = "SELECT * FROM khach_hang WHERE LOWER(ho_ten) LIKE CONCAT('%', LOWER(:ten), '%') AND ma_vai_tro = 3", nativeQuery = true)
    public List<KhachHang> findAllKhach(@Param("ten") String ten);

    @Query(value = "SELECT * FROM khach_hang WHERE ma_khach_hang <> :id", nativeQuery = true)
    public List<KhachHang> findOthers(@Param("id") Integer id);

    public KhachHang findByHoTen(String name);

    boolean existsByEmailAndVaiTro_TenVaiTro(String email, String roleAdmin);








}
