package su26sd09.su26sd09.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import su26sd09.su26sd09.entity.ChiTietDatPhong;
import su26sd09.su26sd09.entity.DatPhong;
import su26sd09.su26sd09.entity.Phong;
import su26sd09.su26sd09.service.ChiTietDatPhongService;
import su26sd09.su26sd09.service.ChiTietDichVuService;
import su26sd09.su26sd09.service.DatPhongService;
import su26sd09.su26sd09.service.PhongService;

import java.time.LocalDateTime;
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

            System.out.println("Da xoa don rac ma dat phong: " + dp.getId());

        }
        if (!DonQuaHan.isEmpty()) {
            System.out.println("Scheduler: da xoa " + DonQuaHan.size() + " don qua han.");
        }

    }
}
