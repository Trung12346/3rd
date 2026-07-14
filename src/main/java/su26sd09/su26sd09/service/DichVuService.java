package su26sd09.su26sd09.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import su26sd09.su26sd09.entity.Dich_vu;
import su26sd09.su26sd09.repository.ChiTietDichvuRepo;
import su26sd09.su26sd09.repository.DichVuRepo;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class DichVuService {

    @Autowired
    DichVuRepo dichVuRepo;

    @Autowired
    ChiTietDichvuRepo chiTietDichvuRepo;

    public List<Dich_vu> findAll(){
        return dichVuRepo.findAll();
    }

    public Dich_vu findById(Integer id){
        return dichVuRepo.findById(id).orElse(null);
    }

    public List<Dich_vu> search(String keyword, String trangThai, String loaiDichVu){
        return dichVuRepo.search(keyword, trangThai, loaiDichVu);
    }

    /** Danh sách dịch vụ THƯỜNG đang hoạt động — dùng cho dropdown "Dịch vụ thường" khi thêm vào đơn. */
    public List<Dich_vu> findActiveThuong(){
        return dichVuRepo.findActiveByLoai("THUONG");
    }

    /** Danh sách dịch vụ PHÁT SINH đang hoạt động — dùng cho dropdown "Dịch vụ phát sinh" khi thêm vào đơn. */
    public List<Dich_vu> findActivePhatSinh(){
        return dichVuRepo.findActiveByLoai("PHAT_SINH");
    }

    public Dich_vu save(Dich_vu dv){
        return dichVuRepo.save(dv);
    }

    public void deleteById(Integer id){
        dichVuRepo.deleteById(id);
    }

    public long countAll(){
        return dichVuRepo.count();
    }

    public long countActive(){
        return dichVuRepo.countActive();
    }

    public Map<Integer, Long> soLuongSuDungTheoDichVu(){
        Map<Integer, Long> map = new HashMap<>();
        for (Object[] row : chiTietDichvuRepo.thongKeSoLuongTheoDichVu()){
            Integer maDichVu = (Integer) row[0];
            Long tong = ((Number) row[1]).longValue();
            map.put(maDichVu, tong);
        }
        return map;
    }

    public Dich_vu dichVuDuocSuDungNhieuNhat(){
        List<Object[]> thongKe = chiTietDichvuRepo.thongKeSoLuongTheoDichVu();
        if (thongKe.isEmpty()) return null;
        Integer maDichVu = (Integer) thongKe.get(0)[0];
        return findById(maDichVu);
    }

    public BigDecimal tongTienDichVu(){
        BigDecimal tong = chiTietDichvuRepo.tongTienDichVu();
        return tong != null ? tong : BigDecimal.ZERO;
    }

    /** Tìm dịch vụ phát sinh đã tồn tại theo tên + đơn giá để tái sử dụng (không tạo trùng dòng master). */
    public Optional<Dich_vu> findPhatSinhTheoTenVaGia(String ten, BigDecimal gia) {
        if (ten == null || ten.isBlank() || gia == null) return Optional.empty();
        return dichVuRepo.findByTenAndGia(ten.trim(), gia).stream()
                .filter(d -> "PHAT_SINH".equals(d.getLoaiDv()))
                .findFirst();
    }

    /** Tạo mới 1 dịch vụ master loại PHAT_SINH từ mô tả + đơn giá nhập tay trong chi-tiet-dat-phong. */
    public Dich_vu taoDichVuPhatSinhMoi(String ten, BigDecimal gia) {
        Dich_vu dv = new Dich_vu();
        dv.setTen_dich_vu(ten);
        dv.setGia(gia);
        dv.setDonVi("vụ");
        dv.setLoaiDv("PHAT_SINH");
        dv.setHoatDong(true);
        return dichVuRepo.save(dv);
    }

    /** Trả về map: maDichVu -> danh sách maDatPhong đã dùng dịch vụ này (cho cột "Đơn phát sinh" ở dich-vu-list). */
    public Map<Integer, List<Integer>> maDatPhongTheoDichVu() {
        Map<Integer, List<Integer>> map = new HashMap<>();
        for (Object[] row : dichVuRepo.findMaDatPhongTheoDichVu()) {
            Integer maDv = (Integer) row[0];
            Integer maDp = (Integer) row[1];
            if (maDv == null || maDp == null) continue;
            map.computeIfAbsent(maDv, k -> new ArrayList<>()).add(maDp);
        }
        return map;
    }
}