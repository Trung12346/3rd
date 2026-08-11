package su26sd09.su26sd09.service;

import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import su26sd09.su26sd09.entity.DatPhong;
import su26sd09.su26sd09.entity.KhuyenMai;
import su26sd09.su26sd09.repository.ChiTietDatPhongRepo;
import su26sd09.su26sd09.repository.DatPhongRepo;
import su26sd09.su26sd09.repository.khuyenMaiRepo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;

@Service
public class khuyenMaiService {



    @Autowired
    khuyenMaiRepo repo;
    @Autowired
    DatPhongRepo repodatPhong;
    @Autowired
    ChiTietDatPhongService repochitietdatphong;

    public List<KhuyenMai> findAll(){
        return repo.findAll();
    }

    public KhuyenMai findbyId(int id){
        return repo.findById(id).orElse(null);

    }
    public void delete(KhuyenMai m){
        repo.delete(m);
    }

    public void save(KhuyenMai m){
        repo.save(m);

    }

    public Stream<KhuyenMai> findAllActive(){
        return  repo.findAll().stream().filter(km -> km.hoatDong == true);
    }

    public List<KhuyenMai> findbyNameVoucher(String name){
        return repo.findbyPromoCode(name);
    }

    public boolean IsThoaManDieuKienGiam(BigDecimal giatriGiam,BigDecimal dieuKienGiamToiThieu,String loaiGiam){
        if (giatriGiam.floatValue() > dieuKienGiamToiThieu.floatValue() * 50/100 && loaiGiam.equals("AMOUNT")){
            return true;
        }if (giatriGiam.floatValue() > 50.0 && loaiGiam.equals("PERCENT")){
            return true;
        }
        return false;
    }

    public Page<KhuyenMai> search(
            String promoCode,
            String moTa,
            String loaiGiam,
            BigDecimal giatriGiam,
            LocalDate ngayBatDau,
            LocalDate ngayKetThuc,
            Boolean hoatDong,
            int page,
            int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("ma_khuyen_mai").descending());
        System.out.println("đây là size:" + repo.search(
                promoCode,
                moTa,
                loaiGiam,
                giatriGiam,
                ngayBatDau,
                ngayKetThuc,
                hoatDong,
                pageable).getTotalElements());

        return repo.search(
                promoCode,
                moTa,
                loaiGiam,
                giatriGiam,
                ngayBatDau,
                ngayKetThuc,
                hoatDong,
                pageable);
    }


    @Scheduled(cron = "*/10 * * * * *")
    @Transactional
    public void capNhatTrangThaiKhuyenMai() {
        LocalDate today = LocalDate.now();

        repo.kichHoatKhuyenMaiDenNgay(today);

        repo.tatKhuyenMaiHetHan(today);
    }

    public String TimKhuyenMaiDaSuDung(KhuyenMai km){
        boolean isnull = repodatPhong.findFirstByKmId(km.id) == null ? true : false;

        KhuyenMai kmc = isnull == true ? null : repodatPhong.findFirstByKmId(km.id).getKm();

        if (kmc != null ){
            if ( !kmc.ngayKetThuc.equals(km.ngayKetThuc) && kmc.ngayBatDau.equals(km.ngayBatDau))
                return "không thể chỉnh sửa ngày kết thúc của khuyến mãi đã được sử dụng";
            else if (kmc.ngayKetThuc.equals(km.ngayKetThuc) && !kmc.ngayBatDau.equals(km.ngayBatDau))
                return "không thể chỉnh sửa ngày bắt đầu của khuyến mãi đã được sử dụng";
            else if (kmc.ngayBatDau.equals(km.ngayBatDau) && kmc.ngayKetThuc.equals(km.ngayKetThuc));
            return "";
        }
        return "null";
    }


    public String ValidUpdateKhuyenMai(KhuyenMai m){
        String CanChangeDate = TimKhuyenMaiDaSuDung(m);
        DatPhong dp = repodatPhong.findFirstByKmId(m.id);

        if ( dp != null && dp.getKm() != null ){
            if (CanChangeDate != null && !CanChangeDate.equalsIgnoreCase("")){
                return CanChangeDate;
            }

            if (m.hoatDong == false ){
                return "không thể sửa hoạt động mã đã được sử dụng ở đơn đặt phòng " + dp.id;
            }
            else if (!m.loaiGiam.equalsIgnoreCase(dp.getKm().getLoaiGiam())){
                return "không thể sửa loại giảm vì mã đã được sử dụng ở đơn đặt phòng" + dp.id;
            }
            else if (m.giaToiThieuDuocGiam.floatValue() != dp.getKm().giaToiThieuDuocGiam.floatValue()){
                return "không thể sửa giá tối thiểu được giảm vì mã đã được sử dụng ở đơn đặt phòng " + dp.id;
            }
            else if (m.giatriGiam.floatValue() != dp.getKm().giatriGiam.floatValue()){
                return "không thể sửa giá trị giảm vì mã đã được sử dụng ở đơn đặt phòng " + dp.id;
            }
            else if (!m.promoCode.equalsIgnoreCase(dp.getKm().promoCode)){
                return "không thể sửa tên mã khuyến mãi vì mã được sử dụng ở đơn đặt phòng " + dp.id;
            }
            else if (!m.moTa.equalsIgnoreCase(dp.getKm().moTa)){
                return "không thể sửa mô tả vì khuyến mãi đã được sử dụng ở đơn đặt phòng" + dp.id;
            }
        }
        return "null";
    }



    public boolean doesExitsInDatPhong(int id){
        return repodatPhong.findFirstByKmId(id) != null && repochitietdatphong.exitbyDatPhongid(repodatPhong.findFirstByKmId(id).id) == true ? true : false;
    }


    /**
     * Kiểm tra đơn có được phép đổi/gán khuyến mãi hay không.
     * <p>
     * Quy tắc nghiệp vụ MỚI: một đơn đặt phòng được phép đổi khuyến mãi nhiều lần
     * (qua nhiều lần cập nhật), miễn là đơn chưa hoàn tất checkout.
     * <ul>
     *   <li>Cho phép: thêm KM mới, đổi sang KM khác, xóa KM (set null).</li>
     *   <li>Chặn nếu: đơn đã ở trạng thái "Da tra phong" (đã checkout).</li>
     * </ul>
     * @return {@code null} nếu thao tác hợp lệ; ngược lại trả về thông báo lỗi tiếng Việt.
     */
    public String validateGanKhuyenMai(DatPhong dp, KhuyenMai kmMoi) {
        // Chặn nếu đơn đã trả phòng (checkout) - không cho sửa KM sau khi đã xuất hóa đơn
        if (dp.getTrangThai() != null && "Da tra phong".equals(dp.getTrangThai())) {
            return "Đơn #" + dp.getId() + " đã hoàn tất trả phòng, không thể thay đổi khuyến mãi.";
        }
        
        // Cho phép mọi thao tác khác: thêm KM mới, đổi KM, xóa KM
        return null;
    }
}