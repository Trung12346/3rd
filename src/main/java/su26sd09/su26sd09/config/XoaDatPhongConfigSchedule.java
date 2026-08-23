package su26sd09.su26sd09.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import su26sd09.su26sd09.constants.HuyDonConstants;
import su26sd09.su26sd09.entity.ChiTietDatPhong;
import su26sd09.su26sd09.entity.DatPhong;
import su26sd09.su26sd09.entity.Phong;
import su26sd09.su26sd09.service.ChiTietDatPhongService;
import su26sd09.su26sd09.service.ChiTietDichVuService;
import su26sd09.su26sd09.service.CheckInExpirationCacheService;
import su26sd09.su26sd09.service.DatPhongService;
import su26sd09.su26sd09.service.PhongService;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
@Transactional
public class XoaDatPhongConfigSchedule {

    @Autowired
    private DatPhongService datPhongService;

    @Autowired
    private ChiTietDichVuService chiTietDichVuService;

    @Autowired
    private ChiTietDatPhongService chiTietDatPhongService;

    @Autowired
    private PhongService phongService;

    @Autowired
    private CheckInExpirationCacheService checkInExpirationCacheService;

    @Scheduled(fixedRate = 5 * 60 * 1000)
    public void xoaDonQuaHan(){
        System.out.println("Scheduler chay: " + LocalDateTime.now());

        LocalDateTime nguong = LocalDateTime.now().minusMinutes(15);
        List<DatPhong> DonQuaHan = datPhongService.findByTrangThaiAndNgayTaoBefore("Chua thanh toan",nguong);

        for(DatPhong dp : DonQuaHan){
            List<ChiTietDatPhong> chiTietList = chiTietDatPhongService.findByDatPhongId(dp.getId());

            for(ChiTietDatPhong ct : chiTietList){
                Phong p = ct.getP();
                if("Da dat".equals(p.getTrangThai())){
                    p.setTrangThai("Trong");
                    phongService.save1(p);
                }
            }
            chiTietDichVuService.deleteByDatPhongId(dp.getId());
            chiTietDatPhongService.deleteByDatPhongId(dp.getId());
            datPhongService.delete(dp);

            System.out.println  ("Da xoa don rac ma dat phong: " + dp.getId());

        }
        if (!DonQuaHan.isEmpty()) {
            System.out.println("Scheduler: da xoa " + DonQuaHan.size() + " don qua han.");
        }

        xoaYeuCauDatPhongQuaHan();
        xuLyKhachVangQuaHanCheckIn();
    }

    /**
     * Chinh sach no-show: don da duoc XAC NHAN ("Cho xac nhan" / "Da xac nhan")
     * nhung khach chua check-in khi qua han check-in hieu luc (mac dinh 12:00 ngay
     * hom sau ngay_nhan_phong, hoac moc gia han rieng do nhan vien thiet lap qua
     * CheckInExpirationCacheService neu khach da goi dien xin den tre) se:
     *  - Chuyen trang thai don sang "Khach vang".
     *  - Giai phong cac phong dang giu cho don (ve "Trong"), doi xu tuong tu
     *    1 don da checkout (khong con chan lich).
     *
     * Khong ap dung cho "Yeu cau dat phong" (da co luong don rac rieng o
     * xoaYeuCauDatPhongQuaHan) va khong dung toi cac don da check-in/checkout/huy.
     */
    public void xuLyKhachVangQuaHanCheckIn() {
        LocalDateTime bayGio = LocalDateTime.now();
        List<DatPhong> dangTheoDoi = new ArrayList<>();
        for (String tt : HuyDonConstants.DP_TRANG_THAI_AP_DUNG_KHACH_VANG) {
            dangTheoDoi.addAll(datPhongService.findByTrangThai(tt));
        }

        List<DatPhong> khachVang = new ArrayList<>();
        for (DatPhong dp : dangTheoDoi) {
            LocalDateTime han = checkInExpirationCacheService.hanHieuLuc(dp);
            if (han != null && bayGio.isAfter(han)) {
                khachVang.add(dp);
            }
        }

        for (DatPhong dp : khachVang) {
            List<ChiTietDatPhong> chiTietList = chiTietDatPhongService.findByDatPhongId(dp.getId());
            for (ChiTietDatPhong ct : chiTietList) {
                Phong p = ct.getP();
                if (p != null && !"Trong".equals(p.getTrangThai())) {
                    p.setTrangThai("Trong");
                    phongService.save1(p);
                }
            }

            dp.setTrangThai(HuyDonConstants.DP_KHACH_VANG);
            dp.setNgayCapNhat(bayGio);
            datPhongService.save(dp);

            checkInExpirationCacheService.xoaKhoiTheoDoi(dp.getId());

            System.out.println("Da chuyen don sang Khach vang (qua han check-in) ma dat phong: " + dp.getId());
        }

        if (!khachVang.isEmpty()) {
            System.out.println("Scheduler: da xu ly " + khachVang.size() + " don Khach vang.");
        }

        // Don dep cache: chi giu lai gia han cua nhung don van dang duoc theo doi,
        // tranh file cache phinh to voi cac don da xu ly xong tu lau.
        Set<Integer> maConTheoDoi = new HashSet<>();
        for (DatPhong dp : dangTheoDoi) {
            if (!khachVang.contains(dp)) maConTheoDoi.add(dp.getId());
        }
        checkInExpirationCacheService.donDep(maConTheoDoi);
    }

    /**
     * Don o trang thai "Yeu cau dat phong" (khach online gui yeu cau, chua duoc
     * NV xac nhan / chua thanh toan xong) bi xoa tu dong khi:
     *  - Da qua {@link HuyDonConstants#YEU_CAU_DAT_PHONG_HET_HAN_GIO} gio ke tu
     *    luc tao don (ngayTao), HOAC
     *  - Da qua {@link HuyDonConstants#YEU_CAU_DAT_PHONG_GIO_CHOT_QUA_HAN}:00 cua
     *    ngay SAU ngay nhan phong (ngaydatPhong) — chinh sach dat phong: khach
     *    khong the nhan phong tre hon moc nay nen yeu cau khong con y nghia.
     *
     * Don da duoc thanh toan du se KHONG con o trang thai nay (da tu dong chuyen
     * sang "Da xac nhan" qua HoaDonService#saveWithPaymentStatusCheck) nen khong
     * bi anh huong boi viec don rac o day.
     */
    public void xoaYeuCauDatPhongQuaHan() {
        LocalDateTime bayGio = LocalDateTime.now();
        LocalDateTime nguongTao = bayGio.minusHours(HuyDonConstants.YEU_CAU_DAT_PHONG_HET_HAN_GIO);

        List<DatPhong> dsYeuCau = datPhongService.findByTrangThai(HuyDonConstants.DP_YEU_CAU_DAT_PHONG);
        List<DatPhong> donQuaHan = new ArrayList<>();

        for (DatPhong dp : dsYeuCau) {
            boolean quaHanTao = dp.getNgayTao() != null && dp.getNgayTao().isBefore(nguongTao);

            boolean quaHanNhanPhong = false;
            if (dp.getNgaydatPhong() != null) {
                LocalDateTime motQuaHanNhanPhong = dp.getNgaydatPhong().toLocalDate()
                        .plusDays(1)
                        .atTime(HuyDonConstants.YEU_CAU_DAT_PHONG_GIO_CHOT_QUA_HAN, 0);
                quaHanNhanPhong = bayGio.isAfter(motQuaHanNhanPhong);
            }

            if (quaHanTao || quaHanNhanPhong) {
                donQuaHan.add(dp);
            }
        }

        for (DatPhong dp : donQuaHan) {
            List<ChiTietDatPhong> chiTietList = chiTietDatPhongService.findByDatPhongId(dp.getId());

            for (ChiTietDatPhong ct : chiTietList) {
                Phong p = ct.getP();
                if (p != null && "Da dat truoc".equals(p.getTrangThai())) {
                    p.setTrangThai("Trong");
                    phongService.save1(p);
                }
            }
            chiTietDichVuService.deleteByDatPhongId(dp.getId());
            chiTietDatPhongService.deleteByDatPhongId(dp.getId());
            datPhongService.delete(dp);

            System.out.println("Da xoa yeu cau dat phong qua han ma dat phong: " + dp.getId());
        }

        if (!donQuaHan.isEmpty()) {
            System.out.println("Scheduler: da xoa " + donQuaHan.size() + " yeu cau dat phong qua han.");
        }
    }
}
