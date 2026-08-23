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
import su26sd09.su26sd09.service.DatPhongService;
import su26sd09.su26sd09.service.PhongService;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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
