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

        /**
         * Tim kiem phong theo tung truong loc rieng biet (so phong, loai phong,
         * so tang, trang thai), phan trang, sap xep giam dan theo ma phong.
         */
        public org.springframework.data.domain.Page<Phong> searchFiltered(
                String soPhong, Integer loaiPhongId, Integer soTang, String trangThai, int page, int size) {
            org.springframework.data.domain.Pageable pageable =
                    org.springframework.data.domain.PageRequest.of(Math.max(page, 0), size);
            return phongRepository.searchFiltered(soPhong, loaiPhongId, soTang, trangThai, pageable);
        }
    
        public Phong findById(int id) {
            return phongRepository.findById(id).orElse(null);
        }
    
        public Phong findPhongById(int id) {
            return phongRepository.findById(id).orElse(null);
        }
    
        public List<Phong> findAllPhongIncludingInactive() {
            return phongRepository.findAll();
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
    
        /**
         * Phan tich tham so khoang gia dang "min-max" (vd "500000-1000000",
         * don vi VND/dem) thanh cap BigDecimal[minGia, maxGia]. Neu khoangGia
         * null/rong hoac khong dung dinh dang, tra ve {null, null} de bao hieu
         * "khong loc theo gia" (searchLoaiPhong/searchLoaiPhongKhaDung se bo
         * qua dieu kien gia hoan toan trong truong hop nay).
         */
        private BigDecimal[] parseKhoangGia(String khoangGia) {
            if (khoangGia == null || khoangGia.isBlank()) {
                return new BigDecimal[]{null, null};
            }
            String[] parts = khoangGia.trim().split("-");
            if (parts.length != 2) {
                return new BigDecimal[]{null, null};
            }
            try {
                BigDecimal minGia = new BigDecimal(parts[0].trim());
                BigDecimal maxGia = new BigDecimal(parts[1].trim());
                return new BigDecimal[]{minGia, maxGia};
            } catch (NumberFormatException e) {
                return new BigDecimal[]{null, null};
            }
        }

        /**
         * @param khoangGia khoang gia phong/dem dang "min-max" (vd
         *                  "500000-1000000"), hoac null/rong de khong loc theo gia.
         */
        public List<LoaiPhong> searchLoaiPhong(String khoangGia, Integer nguoiLon, Integer treEm) {
            BigDecimal[] khoang = parseKhoangGia(khoangGia);
            BigDecimal minGia = khoang[0];
            BigDecimal maxGia = khoang[1];
    
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
                                                              Integer nguoiLon, Integer treEm, String khoangGia) {
            List<LoaiPhong> ungVien = searchLoaiPhong(khoangGia, nguoiLon, treEm);

            boolean coLocTheoNgay = ngayNhan != null && ngayTra != null;
            java.util.Set<Integer> maPhongDaKhoaLich = coLocTheoNgay
                    ? findMaPhongDaKhoaTrongKhoang(ngayNhan, ngayTra)
                    : java.util.Collections.emptySet();

            List<LoaiPhong> ketQua = new ArrayList<>();
            Map<Integer, Long> soPhongKhaDungTheoLoai = new HashMap<>();

            for (LoaiPhong loai : ungVien) {
                // CHI dua vao chong lan lich (maPhongDaKhoaLich, tinh tu chinh cac don
                // dat con hieu luc voi gio nhan 14:00 / tra 11:00) de xet kha dung theo
                // ngay. KHONG con loc theo trangThai tuc thoi cua phong nua: truong nay
                // chi phan anh trang thai TAI THOI DIEM HIEN TAI (vd "Da dat truoc" ngay
                // khi co bat ky don tuong lai nao, "Dang su dung" khi dang co khach o),
                // nen se loai oan cac phong THUC SU trong trong khoang ngay dang tim
                // kiem chi vi chung dang ban o thoi diem khac (truoc/sau khoang nay).
                // hoatDong=false (ngung hoat dong han) da duoc loc san trong
                // findPhongTheoLoai().
                long soPhongKhaDung = findPhongTheoLoai(loai.getId()).stream()
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
         *  1) Dieu kien du (bat buoc): hoatDong=true, KHONG bi khoa lich (khong
         *     chong lan khoang ngay [ngayNhan, ngayTra) voi bat ky don nao dang
         *     hieu luc). Khong con doi hoi trangThai tuc thoi = "Trong" vi truong
         *     do chi phan anh trang thai TAI THOI DIEM HIEN TAI, khong dai dien
         *     dung cho kha dung trong mot khoang ngay tuong lai cu the.
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

            // CHI dua vao chong lan lich theo ngay (khong con doi hoi trangThai tuc
            // thoi = "Trong"): xem giai thich chi tiet trong searchLoaiPhongKhaDung()
            // o tren - trangThai tuc thoi khong dai dien dung cho kha dung trong MOT
            // KHOANG NGAY TUONG LAI cu the.
            List<Phong> hopLe = findPhongTheoLoai(loaiPhongId).stream()
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

        /**
         * So phong con trong THUC SU cua 1 loai phong trong khoang [ngayNhan,
         * ngayTra) - dung chung engine kha dung voi searchLoaiPhongKhaDung()/
         * assignRoomsForType() (chi dua vao chong lan lich qua
         * findMaPhongDaKhoaTrongKhoang, khong dua vao trangThai tuc thoi).
         * Dung cho FE cap nhat max cua o "So luong phong" ngay khi khach chon
         * ngay nhan/tra, thay vi dung tam so phong "Trong" tinh tai thoi diem
         * hien tai (khong phan anh dung kha dung theo ngay tuong lai).
         *
         * Neu ngayNhan/ngayTra khong hop le (null hoac ngayTra khong sau
         * ngayNhan) thi tra ve so phong hoat dong cua loai (khong loc theo
         * ngay), giu hanh vi fallback giong luc chua chon ngay.
         */
        public long soPhongKhaDungTheoLoaiVaNgay(int loaiPhongId, LocalDateTime ngayNhan, LocalDateTime ngayTra) {
            List<Phong> phongTheoLoai = findPhongTheoLoai(loaiPhongId);
            if (ngayNhan == null || ngayTra == null || !ngayTra.isAfter(ngayNhan)) {
                return phongTheoLoai.size();
            }
            java.util.Set<Integer> maPhongDaKhoaLich = findMaPhongDaKhoaTrongKhoang(ngayNhan, ngayTra);
            return phongTheoLoai.stream()
                    .filter(p -> !maPhongDaKhoaLich.contains(p.getMaPhong()))
                    .count();
        }
    
        public List<LoaiPhong> findLoaiPhongKhac(int id) {
            return loaiPhongRepository.findAllByOrderByTenLoaiAsc()
                    .stream()
                    .filter(loaiPhong -> loaiPhong.getId() != id)
                    .toList();
        }

        // ===== Gio nhan/tra phong CHUAN, dung de tinh "ngay het phong theo loai"
        // (tinhNgayHetPhongTheoLoai). Trung voi gio hardcode o LoaiPhongController
        // (dat-nhanh: nhan 14:00, tra 11:00), khai bao lai o day de tranh phu
        // thuoc nguoc (service khong nen phu thuoc controller). KHONG anh huong
        // gi den bookingGuard / phu phi ngoai gio hien co (do la logic khac).
        private static final LocalTime GIO_NHAN_CHUAN = LocalTime.of(14, 0);
        private static final LocalTime GIO_TRA_CHUAN = LocalTime.of(11, 0);

        /**
         * MOI - khong sua/khong dung lai logic san co theo TUNG PHONG hien co
         * (findMaPhongDaKhoaTrongKhoang, buildRoomGuardFor...). Tinh danh sach
         * cac KHOANG NGAY trong 1 THANG ma 1 LOAI PHONG da HET SACH phong hoat
         * dong (khong con phong nao co the nhan khach vao ngay do), dung cho
         * calendar cua trang chi tiet loai phong (/loai-phong/{id}) disable
         * ngay khong the chon.
         *
         * Xu ly dung gio nhan/tra chuan (14:00 / 11:00) de KHONG bi false
         * negative: 1 ngay D van duoc coi la CON PHONG neu co it nhat 1 phong
         * ma mot dot o toi thieu bat dau tu D 14:00 va ket thuc D+1 11:00
         * (mot dem) KHONG chong lan voi bat ky dot giu cho nao dang hieu luc
         * cua phong do - vi vay khach A co the nhan phong dung ngay khach B
         * tra phong (B tra 11:00 truoc, A nhan 14:00 sau) ma ngay do van
         * duoc phep chon.
         *
         * @param loaiPhongId loai phong can tinh
         * @param thang       thang duong lich can tinh (vi du 2026-08)
         */
        public List<su26sd09.su26sd09.dto.NgayHetPhongDTO> tinhNgayHetPhongTheoLoai(
                int loaiPhongId, java.time.YearMonth thang) {

            List<Phong> phongs = findPhongTheoLoai(loaiPhongId);
            java.util.Set<Integer> maPhongHoatDong = new java.util.HashSet<>();
            for (Phong p : phongs) {
                maPhongHoatDong.add(p.getMaPhong());
            }
            int tongSoPhong = maPhongHoatDong.size();

            java.time.LocalDate ngayDauThang = thang.atDay(1);
            java.time.LocalDate ngayCuoiThang = thang.atEndOfMonth();

            List<su26sd09.su26sd09.dto.NgayHetPhongDTO> ketQua = new ArrayList<>();

            if (tongSoPhong == 0) {
                // Loai phong khong co phong hoat dong nao -> ca thang "het phong".
                ketQua.add(new su26sd09.su26sd09.dto.NgayHetPhongDTO(ngayDauThang, ngayCuoiThang));
                return ketQua;
            }

            // Lay truoc TOAN BO dot giu cho cham vao thang nay (mo rong nhe ve 2
            // phia de khong bo sot booking dang o ngay cuoi thang truoc / dau
            // thang sau nhung van anh huong ngay dau/cuoi thang dang xet), thay vi
            // truy van rieng cho tung ngay (tranh N+1 query, N = so ngay trong thang).
            LocalDateTime tuMoc = ngayDauThang.minusDays(1).atStartOfDay();
            LocalDateTime denMoc = ngayCuoiThang.plusDays(2).atStartOfDay();
            List<su26sd09.su26sd09.dto.LichPhongTheoLoaiProjection> datLich =
                    datPhongRepo.findLichBiKhoaTheoLoaiTrongKhoang(loaiPhongId, tuMoc, denMoc);

            java.time.LocalDate ngayBatDauKhoangHet = null;

            for (java.time.LocalDate ngay = ngayDauThang; !ngay.isAfter(ngayCuoiThang); ngay = ngay.plusDays(1)) {
                // Dot o toi thieu (1 dem) neu khach chon "ngay" lam ngay nhan phong,
                // theo gio chuan: [ngay 14:00, ngay+1 11:00).
                LocalDateTime moGioNhan = ngay.atTime(GIO_NHAN_CHUAN);
                LocalDateTime moGioTra = ngay.plusDays(1).atTime(GIO_TRA_CHUAN);

                java.util.Set<Integer> maPhongDaKhoaNgayNay = new java.util.HashSet<>();
                for (su26sd09.su26sd09.dto.LichPhongTheoLoaiProjection dl : datLich) {
                    if (!maPhongHoatDong.contains(dl.getMaPhong())) {
                        continue; // Bo qua phong da ngung hoat dong (khong con trong pool).
                    }
                    boolean chongLan = dl.getNgayNhan().isBefore(moGioTra) && dl.getNgayTra().isAfter(moGioNhan);
                    if (chongLan) {
                        maPhongDaKhoaNgayNay.add(dl.getMaPhong());
                    }
                }

                boolean hetPhongNgayNay = maPhongDaKhoaNgayNay.size() >= tongSoPhong;

                if (hetPhongNgayNay) {
                    if (ngayBatDauKhoangHet == null) {
                        ngayBatDauKhoangHet = ngay;
                    }
                } else if (ngayBatDauKhoangHet != null) {
                    ketQua.add(new su26sd09.su26sd09.dto.NgayHetPhongDTO(ngayBatDauKhoangHet, ngay.minusDays(1)));
                    ngayBatDauKhoangHet = null;
                }
            }
            if (ngayBatDauKhoangHet != null) {
                ketQua.add(new su26sd09.su26sd09.dto.NgayHetPhongDTO(ngayBatDauKhoangHet, ngayCuoiThang));
            }

            return ketQua;
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
    
        /**
         * So giao dich (dat phong) chua hoan tat (dang giu cho/dang o) con
         * gan voi phong nay. > 0 nghia la KHONG duoc phep xoa (ngung hoat
         * dong) phong.
         */
        public long countGiaoDichChuaHoanTatByPhong(int maPhong) {
            return phongRepository.countGiaoDichChuaHoanTatByPhong(maPhong);
        }

        public void delete(int id) {
            Phong phong = findById(id);
            if (phong != null) {
                phong.setHoatDong(false);
                phong.setNgayCapNhat(LocalDateTime.now());
                phongRepository.save(phong);
            }
        }

        /** Kich hoat lai phong da ngung hoat dong. */
        public void activate(int id) {
            Phong phong = findById(id);
            if (phong != null) {
                phong.setHoatDong(true);
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
                        BigDecimal.ZERO /* phu phi ngoai gio da bo, giu field de tuong thich */,
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
                if ("Da nhan phong".equals(tt) || "Yeu cau dat phong".equals(tt) || "Cho xac nhan".equals(tt) || "Da xac nhan".equals(tt)) {
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
            //   - phu phi ngoai gio  = DA BO (khach den som/tra tre khong con bi tinh phi)
            return new RoomBookingGuardDTO(
                    phong.getTrangThai(),
                    trangThaiDonGanNhat,
                    khoaLich,
                    LocalTime.of(11, 0),
                    LocalTime.of(14, 0),
                    LocalTime.of(11, 0),
                    BigDecimal.ZERO /* phu phi ngoai gio da bo, giu field de tuong thich */,
                    true
            );
        }

        private RoomBookingGuardDTO blockedGuard(String trangThaiPhong, String trangThaiDon) {
            return new RoomBookingGuardDTO(
                    trangThaiPhong, trangThaiDon, java.util.Collections.emptyList(),
                    LocalTime.of(11, 0), LocalTime.of(14, 0), LocalTime.of(11, 0),
                    BigDecimal.ZERO /* phu phi ngoai gio da bo, giu field de tuong thich */,
                    false
            );
        }
    
        /**
     * Phụ phí ngoài giờ (check-in quá sớm / check-out quá trễ so với giờ chuẩn
     * cua guard) ĐÃ ĐƯỢC BỎ theo yêu cầu — luôn trả về ZERO bất kể giờ nhận/tra
     * thực tế. Giữ nguyên chữ ký hàm để không phải sửa các noi goi (DatPhongService,
     * AdminDatPhongController, NhanVienDatPhongController, ...).
     */
    public BigDecimal calculateExtraFeeFor(int maPhong, LocalDateTime ngayNhan, LocalDateTime ngayTra) {
            return BigDecimal.ZERO;
        }

    /**
     * Tính phụ phí cho LUỒNG CHECKOUT TẠI QUẦY (nhân viên bấm "Chốt trả phòng").
     *
     * <p>Ý nghĩa "giờ trả" ở đây là giờ thực tế khách trả = {@code gioTraHienTai}
     * (thường là {@code LocalDateTime.now()}). Công thức đúng:</p>
     * <ul>
     *   <li>Khách trả TRƯỚC hoặc ĐÚNG ngày-giờ đã đặt → 0 phí (kể cả trả trong
     *       cùng ngày nhận — miễn trước {@code ngaytraPhong} là sớm).</li>
     *   <li>Khách trả SAU ngày-giờ đã đặt → cộng {@code phuPhiNgoaiGioVND}
     *       (vì phòng bị giữ thêm — check-out muộn so với hợp đồng).</li>
     * </ul>
     *
     * <p>Không dùng {@link #calculateExtraFeeFor(int, LocalDateTime, LocalDateTime)}
     * cho luồng này vì hàm đó so sánh với giờ "chuẩn" của phòng (vd 11:00) — sẽ
     * tính phí SAI cho mọi khách checkout sau 11:00 dù đúng giờ booking.</p>
     */
    public BigDecimal calculateLateCheckoutFeeFor(int maPhong,
                                                  LocalDateTime ngayDatPhongDuKien,
                                                  LocalDateTime ngayTraDuKien,
                                                  LocalDateTime gioTraHienTai) {
        if (gioTraHienTai == null) {
            return BigDecimal.ZERO;
        }
        // Trả trước/sớm hoặc đúng giờ đã đặt -> 0
        if (!gioTraHienTai.isAfter(ngayTraDuKien)) {
            return BigDecimal.ZERO;
        }
        RoomBookingGuardDTO guard = buildRoomGuardFor(maPhong);
        if (guard == null || guard.getPhuPhiNgoaiGioVND() == null) {
            return BigDecimal.ZERO;
        }
        return guard.getPhuPhiNgoaiGioVND();
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
