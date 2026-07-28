package su26sd09.su26sd09.service;

import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cglib.core.Local;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import su26sd09.su26sd09.entity.KhuyenMai;
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

}