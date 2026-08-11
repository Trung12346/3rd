    package su26sd09.su26sd09.service;
    
    import org.springframework.beans.factory.annotation.Autowired;
    import org.springframework.stereotype.Service;
    import org.springframework.transaction.annotation.Transactional;
    import su26sd09.su26sd09.dto.RoomBookingGuardDTO;
    import su26sd09.su26sd09.entity.Anh;
    import su26sd09.su26sd09.entity.DatPhong;
    import su26sd09.su26sd09.entity.LoaiPhong;
    import su26sd09.su26sd09.entity.Phong;
    import su26sd09.su26sd09.entity.PhongAnh;
    import su26sd09.su26sd09.entity.TienNghi;
    import su26sd09.su26sd09.entity.TienNghiPhong;
    import su26sd09.su26sd09.repository.AnhRepository;
    import su26sd09.su26sd09.repository.DatPhongRepo;
    import su26sd09.su26sd09.repository.LoaiPhongRepository;
    import su26sd09.su26sd09.repository.PhongAnhRepository;
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
    import java.util.UUID;
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
    
        @Autowired
        private PhongAnhRepository phongAnhRepository;
    
        @Autowired
        private AnhRepository anhRepository;
    
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

        /**
         * Trả về tập mã phòng KHÔNG còn trống trong khoảng [ngayNhan, ngayTra)
         * vì đã có đơn đặt phòng khác (đang giữ chỗ / đang ở) chồng lấn khoảng ngày này.
         * Dùng cho tìm kiếm ở trang chủ để loại các phòng không đủ điều kiện,
         * ngay cả khi trạng thái tổng quát của phòng vẫn đang là "Trong".
         */
        public java.util.Set<Integer> findMaPhongDaKhoaTrongKhoang(LocalDateTime ngayNhan, LocalDateTime ngayTra) {
            if (ngayNhan == null || ngayTra == null) {
                return java.util.Collections.emptySet();
            }
            return new java.util.HashSet<>(datPhongRepo.findMaPhongDaKhoaLichTrongKhoang(ngayNhan, ngayTra));
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

        /**
         * Ket qua danh cho trang tim kiem loai phong theo ngay o (booking.com/
         * agoda style): tra ve cac LoaiPhong con it nhat 1 phong THUC SU trong
         * (trang thai "Trong" VA khong bi khoa lich boi don khac chong lan
         * khoang [ngayNhan, ngayTra)), dong thoi khop dieu kien gia/suc chua
         * nhu searchLoaiPhong(). Cac LoaiPhong khong con phong trong nao se
         * KHONG xuat hien trong ket qua (khong tra ve danh sach Phong rieng le).
         *
         * @return Map.Entry gom danh sach LoaiPhong phu hop va so phong con
         *         trong (thuc su, theo ngay) tuong ung cho tung LoaiPhong.
         */
        public LoaiPhongSearchResult searchLoaiPhongKhaDung(LocalDateTime ngayNhan, LocalDateTime ngayTra,
                                                              Integer nguoiLon, Integer treEm, String mucGia) {
            List<LoaiPhong> ungVien = searchLoaiPhong(mucGia, nguoiLon, treEm);

            boolean coLocTheoNgay = ngayNhan != null && ngayTra != null;
            java.util.Set<Integer> maPhongDaKhoaLich = coLocTheoNgay
                    ? findMaPhongDaKhoaTrongKhoang(ngayNhan, ngayTra)
                    : java.util.Collections.emptySet();

            List<LoaiPhong> ketQua = new ArrayList<>();
            Map<Integer, Long> soPhongKhaDungTheoLoai = new HashMap<>();

            for (LoaiPhong loai : ungVien) {
                long soPhongKhaDung = findPhongTheoLoai(loai.getId()).stream()
                        .filter(p -> "Trong".equalsIgnoreCase(p.getTrangThai()))
                        .filter(p -> !maPhongDaKhoaLich.contains(p.getMaPhong()))
                        .count();

                if (soPhongKhaDung > 0) {
                    ketQua.add(loai);
                    soPhongKhaDungTheoLoai.put(loai.getId(), soPhongKhaDung);
                }
            }

            return new LoaiPhongSearchResult(ketQua, soPhongKhaDungTheoLoai);
        }

        /**
         * Ket qua tra ve tu searchLoaiPhongKhaDung(): danh sach LoaiPhong phu
         * hop kem so phong con trong thuc su (theo ngay) cho moi loai.
         */
        public static final class LoaiPhongSearchResult {
            private final List<LoaiPhong> loaiPhongs;
            private final Map<Integer, Long> soPhongKhaDungTheoLoai;

            public LoaiPhongSearchResult(List<LoaiPhong> loaiPhongs, Map<Integer, Long> soPhongKhaDungTheoLoai) {
                this.loaiPhongs = loaiPhongs;
                this.soPhongKhaDungTheoLoai = soPhongKhaDungTheoLoai;
            }

            public List<LoaiPhong> getLoaiPhongs() {
                return loaiPhongs;
            }

            public Map<Integer, Long> getSoPhongKhaDungTheoLoai() {
                return soPhongKhaDungTheoLoai;
            }
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

        /**
         * BOOKING ENGINE: tu dong chon (soLuong) phong con trong thuc su cua
         * 1 loai phong cho khoang [ngayNhan, ngayTra), thay vi de khach tu
         * chon tay tung phong. Dung cho luong dat phong nhanh tu trang tim kiem.
         *
         * Quy tac chon phong (theo thu tu uu tien):
         *  1) Dieu kien du (bat buoc): hoatDong=true, trangThai="Trong",
         *     KHONG bi khoa lich (khong trung khoang ngay voi don dang giu cho).
         *  2) Uu tien phong co it luot dat gan day nhat (findAllByPhong size nho nhat)
         *     de trai deu muc su dung giua cac phong cung loai.
         *  3) Tie-break: tang dan theo so tang (soTang).
         *  4) Tie-break cuoi: tang dan theo so phong (soPhong) de ket qua on dinh,
         *     de kiem thu.
         *
         * Neu khong du (soLuong) phong hop le tai thoi diem goi -> nem
         * IllegalStateException voi thong bao ro rang; KHONG dat mot phan.
         */
        @Transactional
        public synchronized List<Phong> assignRoomsForType(int loaiPhongId, int soLuong,
                                                             LocalDateTime ngayNhan, LocalDateTime ngayTra) {
            if (soLuong <= 0) {
                throw new IllegalArgumentException("So luong phong can dat phai lon hon 0.");
            }
            if (ngayNhan == null || ngayTra == null || !ngayTra.isAfter(ngayNhan)) {
                throw new IllegalArgumentException("Ngay nhan/tra phong khong hop le.");
            }

            java.util.Set<Integer> maPhongDaKhoaLich = findMaPhongDaKhoaTrongKhoang(ngayNhan, ngayTra);

            List<Phong> hopLe = findPhongTheoLoai(loaiPhongId).stream()
                    .filter(p -> "Trong".equalsIgnoreCase(p.getTrangThai()))
                    .filter(p -> !maPhongDaKhoaLich.contains(p.getMaPhong()))
                    .toList();

            // Tinh truoc so luot dat cho tung phong (1 lan/phong) thay vi goi lai
            // trong comparator, de tranh N+1 query va tranh loi
            // "Comparison method violates its general contract" neu du lieu
            // thay doi giua cac lan so sanh.
            Map<Integer, Integer> soLuotDatTheoPhong = new HashMap<>();
            for (Phong p : hopLe) {
                soLuotDatTheoPhong.put(p.getMaPhong(), phongRepository.findAllByPhong(p.getMaPhong()).size());
            }

            List<Phong> ungVien = hopLe.stream()
                    .sorted((a, b) -> {
                        int soLuotA = soLuotDatTheoPhong.getOrDefault(a.getMaPhong(), 0);
                        int soLuotB = soLuotDatTheoPhong.getOrDefault(b.getMaPhong(), 0);
                        if (soLuotA != soLuotB) return Integer.compare(soLuotA, soLuotB);
                        if (a.getSoTang() != b.getSoTang()) return Integer.compare(a.getSoTang(), b.getSoTang());
                        return a.getSoPhong().compareTo(b.getSoPhong());
                    })
                    .toList();

            if (ungVien.size() < soLuong) {
                throw new IllegalStateException(
                        "Khong du phong trong cho loai phong nay trong khoang ngay da chon (con "
                                + ungVien.size() + "/" + soLuong + " phong).");
            }

            return new ArrayList<>(ungVien.subList(0, soLuong));
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
    
        public List<Anh> findAnhByPhong(int maPhong) {
            return phongAnhRepository.findByMaPhong_MaPhong(maPhong)
                    .stream()
                    .map(PhongAnh::getMaAnh)
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
            save(phong, loaiPhongId, tienNghiIds, null);
        }
    
        @Transactional
        public void save(Phong phong, int loaiPhongId, List<Integer> tienNghiIds, List<UUID> anhIds) {
            LoaiPhong loaiPhong = loaiPhongRepository.findById(loaiPhongId).orElse(null);
            phong.setLoaiPhong(loaiPhong);
            if (loaiPhong == null) throw new RuntimeException("Loại phòng không tồn tại");
    
            if (phong.getMaPhong() == 0) {
                phong.setNgayTao(LocalDateTime.now());
                phong.setNgayCapNhat(LocalDateTime.now());
                Phong savedPhong = phongRepository.save(phong);
                saveTienNghiPhong(savedPhong, tienNghiIds);
                saveAnhPhong(savedPhong, anhIds);
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
            saveAnhPhong(savedPhong, anhIds);
        }
    
        /**
         * Đồng bộ danh sách ảnh của phòng: xóa hết liên kết cũ trong phong_anh
         * rồi tạo lại theo danh sách maAnh gửi lên từ form (giữ ảnh cũ được tick chọn
         * + thêm ảnh mới vừa upload qua AnhController, ảnh nào bị bỏ chọn/xóa sẽ mất liên kết).
         * Bản ghi Anh gốc không bị xóa, chỉ liên kết phong_anh bị xóa.
         */
        private void saveAnhPhong(Phong phong, List<UUID> anhIds) {
            phongAnhRepository.deleteByMaPhong_MaPhong(phong.getMaPhong());
    
            if (anhIds == null || anhIds.isEmpty()) {
                return;
            }
    
            List<Anh> anhs = anhRepository.findAllById(anhIds);
    
            List<PhongAnh> phongAnhs = new ArrayList<>();
            for (Anh anh : anhs) {
                PhongAnh phongAnh = new PhongAnh();
                phongAnh.setMaPhong(phong);
                phongAnh.setMaAnh(anh);
                phongAnhs.add(phongAnh);
            }
    
            phongAnhRepository.saveAll(phongAnhs);
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
                        LocalTime.of(11, 0), LocalTime.of(14, 0), LocalTime.of(11, 0),
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
            //
            // Quy tac gio mac dinh cho DON DAT ONLINE (khach dat qua web):
            //   - gio nhan toi thieu = 11:00 (khong cho nhan som hon 11:00)
            //   - gio nhan toi da    = 14:00 (gio nhan phong mac dinh)
            //   - gio tra toi da     = 11:00 (gio tra phong mac dinh)
            //   - phu phi ngoai gio  = 100.000 VND / phong / lan
            // Khach den som hon 11:00 hoac tra sau 11:00 -> +100k/phong.
            return new RoomBookingGuardDTO(
                    phong.getTrangThai(),
                    trangThaiDonGanNhat,
                    khoaLich,
                    LocalTime.of(11, 0),
                    LocalTime.of(14, 0),
                    LocalTime.of(11, 0),
                    new BigDecimal("100000"),
                    true
            );
        }

        private RoomBookingGuardDTO blockedGuard(String trangThaiPhong, String trangThaiDon) {
            return new RoomBookingGuardDTO(
                    trangThaiPhong, trangThaiDon, java.util.Collections.emptyList(),
                    LocalTime.of(11, 0), LocalTime.of(14, 0), LocalTime.of(11, 0),
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

    /**
     * Build JSON danh sách phòng kèm khoaLich cho frontend (tái sử dụng ở
     * cả form đặt phòng tại quầy và form đổi phòng trong chi tiết đơn).
     *
     * Output mỗi phần tử:
     *   { "maPhong": int, "trangThai": "Trong"|"Dang su dung"|...,
     *     "trangThaiDon": "Da nhan phong"|null,
     *     "khoaLich": [{ "tu": "...", "den": "...", "trangThai": "..." }] }
     *
     * Trả về JSON chưa có cặp dấu [ ] bao ngoài — controller tự wrap.
     */
    public String buildRoomStatusJson(List<Phong> tatCaPhong) {
        Map<Integer, RoomBookingGuardDTO> roomGuards = buildRoomGuards(tatCaPhong);
        StringBuilder rb = new StringBuilder();
        for (int i = 0; i < tatCaPhong.size(); i++) {
            Phong p = tatCaPhong.get(i);
            RoomBookingGuardDTO guard = roomGuards.get(p.getMaPhong());
            String trangThaiDon = guard != null ? guard.getTrangThaiDonGanNhat() : null;

            StringBuilder khoaLichArr = new StringBuilder("[");
            if (guard != null) {
                List<su26sd09.su26sd09.dto.KhoangNgayBiKhoaDTO> danhSach = guard.getDanhSachKhoaLich();
                for (int j = 0; j < danhSach.size(); j++) {
                    su26sd09.su26sd09.dto.KhoangNgayBiKhoaDTO k = danhSach.get(j);
                    if (j > 0) khoaLichArr.append(",");
                    khoaLichArr.append("{")
                            .append("\"tu\":\"").append(k.getNgayBatDau() != null ? k.getNgayBatDau() : "").append("\",")
                            .append("\"den\":\"").append(k.getNgayKetThuc() != null ? k.getNgayKetThuc() : "").append("\",")
                            .append("\"trangThai\":\"").append(escapeJsonStr(k.getTrangThaiDon())).append("\"")
                            .append("}");
                }
            }
            khoaLichArr.append("]");

            if (i > 0) rb.append(",");
            rb.append("{")
                    .append("\"maPhong\":").append(p.getMaPhong()).append(",")
                    .append("\"trangThai\":\"").append(escapeJsonStr(p.getTrangThai())).append("\",")
                    .append("\"trangThaiDon\":").append(trangThaiDon == null ? "null" : "\"" + escapeJsonStr(trangThaiDon) + "\"").append(",")
                    .append("\"khoaLich\":").append(khoaLichArr)
                    .append("}");
        }
        return rb.toString();
    }

    private static String escapeJsonStr(String s) {
        if (s == null) return "";
        StringBuilder out = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':  out.append("\\\""); break;
                case '\\': out.append("\\\\"); break;
                case '\n': out.append("\\n"); break;
                case '\r': out.append("\\r"); break;
                case '\t': out.append("\\t"); break;
                default:
                    if (c < 0x20) out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
            }
        }
        return out.toString();
    }
}
