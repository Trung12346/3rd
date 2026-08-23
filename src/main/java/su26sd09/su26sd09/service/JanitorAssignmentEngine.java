package su26sd09.su26sd09.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import su26sd09.su26sd09.dto.PhongVeSinhAssignment;
import su26sd09.su26sd09.entity.NhanSu;
import su26sd09.su26sd09.entity.Phong;
import su26sd09.su26sd09.repository.NhanVienRepo;
import su26sd09.su26sd09.repository.PhongRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * "Janitor assignment engine": chạy nền trên thread lập lịch riêng của
 * Spring (khác hẳn thread xử lý HTTP request), định kỳ quét toàn bộ phòng
 * đang ở trạng thái "Dang don" (Đang dọn) và tự động gán ngẫu nhiên một
 * nhân viên vệ sinh (bộ phận "Vệ Sinh") hiện KHÔNG đang được gán cho phòng
 * nào khác. Kết quả phân công được lưu trong {@link JanitorCacheService},
 * đồng thời được đồng bộ ra file JSON ở project root để không bị mất khi
 * ứng dụng khởi động lại.
 */
@Component
public class JanitorAssignmentEngine {

    private static final Set<String> TRANG_THAI_CAN_DON = Set.of("Dang don", "Đang dọn");

    @Autowired
    private PhongRepository phongRepository;

    @Autowired
    private NhanVienRepo nhanVienRepo;

    @Autowired
    private JanitorCacheService cacheService;

    @Autowired
    private NhanVienService nhanVienService;

    /**
     * Quét mỗi 5 giây. fixedDelay đảm bảo lần chạy sau chỉ bắt đầu sau khi
     * lần chạy trước đã xong, tránh 2 lần quét chồng lấn nhau gây phân công
     * trùng lặp.
     */
    @Scheduled(fixedDelay = 5000, initialDelay = 3000)
    public void quetVaPhanCong() {
        List<Phong> tatCaPhong = phongRepository.findAll();

        // Don dep cache: bo cac ban ghi ma phong tuong ung khong con o trang
        // thai "Dang don" nua (vd: quan tri thu cong doi trang thai / bao tri)
        // de giai phong nhan vien vinh vien bi ket.
        for (PhongVeSinhAssignment a : new ArrayList<>(cacheService.getAll())) {
            Phong p = tatCaPhong.stream().filter(x -> x.getMaPhong() == a.getMaPhong()).findFirst().orElse(null);
            if (p == null || !TRANG_THAI_CAN_DON.contains(p.getTrangThai())) {
                cacheService.remove(a.getMaPhong());
            }
        }

        List<Phong> canDonChuaGan = tatCaPhong.stream()
                .filter(p -> TRANG_THAI_CAN_DON.contains(p.getTrangThai()))
                .filter(p -> !cacheService.isAssigned(p.getMaPhong()))
                .collect(Collectors.toList());

        if (canDonChuaGan.isEmpty()) {
            return;
        }

        for (Phong phong : canDonChuaGan) {
            NhanSu nhanVienDuocGan = timNhanVienVeSinhRanh();
            if (nhanVienDuocGan == null) {
                // het nhan vien vsanh, cho vong quet sau
                break;
            }

            PhongVeSinhAssignment assignment = new PhongVeSinhAssignment();
            assignment.setMaPhong(phong.getMaPhong());
            assignment.setSoPhong(phong.getSoPhong());
            assignment.setMaNhanVien(nhanVienDuocGan.getId());
            assignment.setTenNhanVien(nhanVienDuocGan.getHoten());
            assignment.setTrangThai(PhongVeSinhAssignment.DA_GAN);
            assignment.setThoiGianGan(LocalDateTime.now().toString());

            cacheService.upsert(assignment);
        }
    }

    private NhanSu timNhanVienVeSinhRanh() {
        Set<Integer> dangBanRon = cacheService.getAll().stream()
                .map(PhongVeSinhAssignment::getMaNhanVien)
                .collect(Collectors.toSet());

        List<NhanSu> ranhViec = nhanVienRepo.findAll().stream()
                .filter(NhanSu::isTrang_thai)
                .filter(n -> nhanVienService.laBoPhanVeSinh(n.getBoPhan()))
                .filter(n -> !dangBanRon.contains(n.getId()))
                .collect(Collectors.toList());

        if (ranhViec.isEmpty()) {
            return null;
        }
        return ranhViec.get(ThreadLocalRandom.current().nextInt(ranhViec.size()));
    }
}
