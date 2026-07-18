package su26sd09.su26sd09.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import su26sd09.su26sd09.dto.RoomBookingGuardDTO;
import su26sd09.su26sd09.entity.DatPhong;
import su26sd09.su26sd09.entity.LoaiPhong;
import su26sd09.su26sd09.entity.Phong;
import su26sd09.su26sd09.entity.TienNghi;
import su26sd09.su26sd09.entity.TienNghiPhong;
import su26sd09.su26sd09.repository.DatPhongRepo;
import su26sd09.su26sd09.repository.LoaiPhongRepository;
import su26sd09.su26sd09.repository.PhongRepository;
import su26sd09.su26sd09.repository.TienNghiPhongRepository;
import su26sd09.su26sd09.repository.TienNghiRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import su26sd09.su26sd09.dto.RoomBookingGuardDTO;

import static org.thymeleaf.util.StringUtils.contains;

@Service
public class PhongService {

    @Autowired
    private PhongRepository phongRepository;

    @Autowired
    private LoaiPhongRepository loaiPhongRepository;

    @Autowired
    private TienNghiRepository tienNghiRepository;

    @Autowired
    private TienNghiPhongRepository tienNghiPhongRepository;

    @Autowired
    private DatPhongRepo datPhongRepo;

    public List<Phong> search(String keyword) {
        return phongRepository.search(keyword);
    }

    public Phong findById(int id) {
        return phongRepository.findById(id).orElse(null);
    }

    public Phong findPhongById(int id) {
        return phongRepository.findById(id).orElse(null);
    }

    public List<Phong> findAllPhong() {
        return phongRepository.findByHoatDongTrueOrderBySoPhongAsc();
    }

    public List<LoaiPhong> findAllLoai() {
        return loaiPhongRepository.findAllByOrderByTenLoaiAsc();
    }

    public List<LoaiPhong> searchLoaiPhong(String mucGia, Integer nguoiLon, Integer treEm) {
        BigDecimal minGia = null;
        BigDecimal maxGia = null;

        if ("duoi1tr".equals(mucGia)) {
            maxGia = new BigDecimal("1000000");
        } else if ("1tr-2tr".equals(mucGia)) {
            minGia = new BigDecimal("1000000");
            maxGia = new BigDecimal("2000000");
        } else if ("tren2tr".equals(mucGia)) {
            minGia = new BigDecimal("2000000");
        }

        Integer soKhach = null;
        if (nguoiLon != null || treEm != null) {
            soKhach = (nguoiLon == null ? 0 : nguoiLon) + (treEm == null ? 0 : treEm);
        }

        return loaiPhongRepository.searchLoaiPhong(minGia, maxGia, soKhach);
    }

    public LoaiPhong findLoaiPhongById(int id) {
        return loaiPhongRepository.findById(id).orElse(null);
    }

    public List<LoaiPhong> searchLoaiPhongAdmin(String keyword) {
        List<LoaiPhong> loaiPhongs = loaiPhongRepository.findAllByOrderByTenLoaiAsc();
        if (keyword == null || keyword.isBlank()) {
            return loaiPhongs;
        }

        String q = keyword.toLowerCase(Locale.ROOT);
        return loaiPhongs.stream()
                .filter(lp -> contains(lp.getTenLoai(), q)
                        || contains(lp.getMota(), q)
                        || String.valueOf(lp.getSucChuaToiDa()).contains(q)
                        || (lp.getGiaCoBan() != null && lp.getGiaCoBan().toPlainString().contains(q)))
                .toList();
    }

    public void saveLoaiPhong(LoaiPhong loaiPhong) {
        loaiPhongRepository.save(loaiPhong);
    }

    public void deleteLoaiPhong(int id) {
        loaiPhongRepository.deleteById(id);
    }

    public List<Phong> findPhongTheoLoai(int loaiPhongId) {
        return phongRepository.findByLoaiPhongIdAndHoatDongTrueOrderBySoPhongAsc(loaiPhongId);
    }

    public long countPhongTrongTheoLoai(int loaiPhongId) {
        return phongRepository.countByLoaiPhongIdAndHoatDongTrueAndTrangThai(loaiPhongId, "Trong");
    }

    public List<LoaiPhong> findLoaiPhongKhac(int id) {
        return loaiPhongRepository.findAllByOrderByTenLoaiAsc()
                .stream()
                .filter(loaiPhong -> loaiPhong.getId() != id)
                .toList();
    }

    public List<TienNghi> findAllTienNghi() {
        return tienNghiRepository.findAllByOrderByTenTienNghiAsc();
    }

    public TienNghi findTienNghiById(int id) {
        return tienNghiRepository.findById(id).orElse(null);
    }

    public List<TienNghi> searchTienNghiAdmin(String keyword) {
        List<TienNghi> tienNghis = tienNghiRepository.findAllByOrderByTenTienNghiAsc();
        if (keyword == null || keyword.isBlank()) {
            return tienNghis;
        }

        String q = keyword.toLowerCase(Locale.ROOT);
        return tienNghis.stream()
                .filter(tn -> contains(tn.getTenTienNghi(), q)
                        || (tn.getMaTienNghi() != null && String.valueOf(tn.getMaTienNghi()).contains(q)))
                .toList();
    }

    public void saveTienNghi(TienNghi tienNghi) {
        tienNghiRepository.save(tienNghi);
    }

    public void deleteTienNghi(int id) {
        tienNghiRepository.deleteById(id);
    }

    public List<Integer> findTienNghiIdsByPhong(int maPhong) {
        return tienNghiPhongRepository.findByPhongMaPhong(maPhong)
                .stream()
                .map(tnp -> tnp.getTienNghi().getMaTienNghi())
                .toList();
    }

    public List<String> findTenTienNghiByPhong(int maPhong) {
        return tienNghiPhongRepository.findByPhongMaPhong(maPhong)
                .stream()
                .map(tnp -> tnp.getTienNghi().getTenTienNghi())
                .toList();
    }
    public Phong save1(Phong p){
        return phongRepository.save(p);
    }
    public List<Phong> findByTrangThai(String trangThai) {
        return phongRepository.findByTrangThai(trangThai);
    }

    @Transactional
    public void save(Phong phong, int loaiPhongId, List<Integer> tienNghiIds) {
        LoaiPhong loaiPhong = loaiPhongRepository.findById(loaiPhongId).orElse(null);
        phong.setLoaiPhong(loaiPhong);
        if (loaiPhong == null) throw new RuntimeException("Loại phòng không tồn tại");

        if (phong.getMaPhong() == 0) {
            phong.setNgayTao(LocalDateTime.now());
            phong.setNgayCapNhat(LocalDateTime.now());
            Phong savedPhong = phongRepository.save(phong);
            saveTienNghiPhong(savedPhong, tienNghiIds);
            return;
        }

        Phong oldPhong = findById(phong.getMaPhong());
        if (oldPhong == null) {
            return;
        }

        oldPhong.setLoaiPhong(phong.getLoaiPhong());
        oldPhong.setSoPhong(phong.getSoPhong());
        oldPhong.setSoTang(phong.getSoTang());
        oldPhong.setGiaMoiDem(phong.getGiaMoiDem());
        oldPhong.setTrangThai(phong.getTrangThai());
        oldPhong.setMoTa(phong.getMoTa());
        oldPhong.setHoatDong(phong.isHoatDong());
        oldPhong.setNgayCapNhat(LocalDateTime.now());

        Phong savedPhong = phongRepository.save(oldPhong);
        saveTienNghiPhong(savedPhong, tienNghiIds);
    }

    private void saveTienNghiPhong(Phong phong, List<Integer> tienNghiIds) {
        tienNghiPhongRepository.deleteByPhongMaPhong(phong.getMaPhong());

        if (tienNghiIds == null || tienNghiIds.isEmpty()) {
            return;
        }

        List<TienNghiPhong> tienNghiPhongs = new ArrayList<>();
        List<TienNghi> tienNghis = tienNghiRepository.findAllById(tienNghiIds);

        for (TienNghi tienNghi : tienNghis) {
            TienNghiPhong tienNghiPhong = new TienNghiPhong();
            tienNghiPhong.setPhong(phong);
            tienNghiPhong.setTienNghi(tienNghi);
            tienNghiPhongs.add(tienNghiPhong);
        }

        tienNghiPhongRepository.saveAll(tienNghiPhongs);
    }

    public void delete(int id) {
        Phong phong = findById(id);
        if (phong != null) {
            phong.setHoatDong(false);
            phong.setNgayCapNhat(LocalDateTime.now());
            phongRepository.save(phong);
        }
    }

    /**
     * Xây map ràng buộc đặt phòng cho từng phòng trong danh sách.
     * Phòng "Trong" -> cho phép đặt, không ràng buộc.
     * Phòng "Dang su dung":
     *  - Đơn Da nhan phong -> coTheDat = true, set khoảng ngày bị gạch chéo.
     *  - Đơn Da tra phong -> coTheDat = true, áp dụng ràng buộc giờ + phụ phí.
     *  - Khác -> coTheDat = false.
     */
    public Map<Integer, RoomBookingGuardDTO> buildRoomGuards(List<Phong> phongs) {
        Map<Integer, RoomBookingGuardDTO> map = new HashMap<>();
        if (phongs == null) {
            return map;
        }
        for (Phong phong : phongs) {
            map.put(phong.getMaPhong(), buildSingleRoomGuard(phong));
        }
        return map;
    }

    /** Trả về guard cho 1 phòng cụ thể. */
    public RoomBookingGuardDTO buildRoomGuardFor(int maPhong) {
        Phong phong = findById(maPhong);
        if (phong == null) {
            return new RoomBookingGuardDTO(
                    null, null, java.util.Collections.emptyList(),
                    LocalTime.of(8, 30), LocalTime.of(11, 0), LocalTime.of(18, 30),
                    new BigDecimal("100000"),
                    false
            );
        }
        return buildSingleRoomGuard(phong);
    }

    private RoomBookingGuardDTO buildSingleRoomGuard(Phong phong) {

        List<DatPhong> allBookings = datPhongRepo.findRecentBookingsForPhong(phong.getMaPhong());

        List<su26sd09.su26sd09.dto.KhoangNgayBiKhoaDTO> khoaLich = new ArrayList<>();
        String trangThaiDonGanNhat = null;

        for (DatPhong dp : allBookings) {
            String tt = dp.getTrangThai();

            // Đơn đầu tiên trong danh sách (sắp theo ngayTao desc) đại diện cho
            // "đơn gần nhất" — dùng để áp rule giờ/phụ phí khi phòng vừa trả.
            if (trangThaiDonGanNhat == null) {
                trangThaiDonGanNhat = tt;
            }

            // Chỉ các trạng thái này mới thực sự khóa lịch (chặn overlap ngày).
            if ("Da nhan phong".equals(tt) || "Cho xac nhan".equals(tt) || "Da xac nhan".equals(tt)) {
                khoaLich.add(new su26sd09.su26sd09.dto.KhoangNgayBiKhoaDTO(
                        dp.getId(), dp.getNgaydatPhong(), dp.getNgaytraPhong(), tt));
            }
        }

        // coTheDat = true luôn (phòng đã tồn tại). Việc chặn cụ thể theo ngày
        // nào sẽ do validateRoomBookingGuard() ở Controller quyết định dựa vào
        // danhSachKhoaLich, không còn phụ thuộc Phong.trangThai nữa.
        return new RoomBookingGuardDTO(
                phong.getTrangThai(),
                trangThaiDonGanNhat,
                khoaLich,
                LocalTime.of(8, 30),
                LocalTime.of(11, 0),
                LocalTime.of(18, 30),
                new BigDecimal("100000"),
                true
        );
    }

    private RoomBookingGuardDTO blockedGuard(String trangThaiPhong, String trangThaiDon) {
        return new RoomBookingGuardDTO(
                trangThaiPhong, trangThaiDon, java.util.Collections.emptyList(),
                LocalTime.of(8, 30), LocalTime.of(11, 0), LocalTime.of(18, 30),
                new BigDecimal("100000"),
                false
        );
    }

    /**
     * Tính phụ phí ngoài giờ cho 1 phòng dựa trên guard và khoảng ngày đặt.
     * Tái sử dụng cùng rule với PhongController.calculateExtraFee() và GioHangController.calculateExtraFee():
     *   - giờ nhận ngoài [gioNhanToiThieu, gioNhanToiDa] -> cộng phuPhiNgoaiGioVND
     *   - giờ trả sau gioTraToiDa                       -> cộng phuPhiNgoaiGioVND
     * Nếu trong khoảng hợp lệ, trả về ZERO.
     */
    public BigDecimal calculateExtraFeeFor(int maPhong, LocalDateTime ngayNhan, LocalDateTime ngayTra) {
        if (ngayNhan == null || ngayTra == null) {
            return BigDecimal.ZERO;
        }
        RoomBookingGuardDTO guard = buildRoomGuardFor(maPhong);
        if (guard == null) {
            return BigDecimal.ZERO;
        }

        LocalTime gioNhan = ngayNhan.toLocalTime();
        LocalTime gioTra = ngayTra.toLocalTime();
        boolean ngoaiGioNhan = gioNhan.isBefore(guard.getGioNhanToiThieu())
                || gioNhan.isAfter(guard.getGioNhanToiDa());
        boolean ngoaiGioTra = gioTra.isAfter(guard.getGioTraToiDa());
        return (ngoaiGioNhan || ngoaiGioTra) ? guard.getPhuPhiNgoaiGioVND() : BigDecimal.ZERO;
    }

    private Optional<DatPhong> findLatestBooking(int maPhong) {

        List<DatPhong> list = datPhongRepo.findRecentBookingsForPhong(maPhong);

        System.out.println("===== FIND LATEST BOOKING =====");
        for (DatPhong dp : list) {
            System.out.println(
                    dp.getId()
                            + " | "
                            + dp.getTrangThai()
                            + " | "
                            + dp.getNgayTao()
            );
        }

        if (list == null || list.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(list.get(0));
    }
}
