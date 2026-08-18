package su26sd09.su26sd09.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import su26sd09.su26sd09.dto.LichPhongDichVuDTO;
import su26sd09.su26sd09.dto.LichPhongEventDTO;
import su26sd09.su26sd09.dto.LichPhongLoaiPhongDTO;
import su26sd09.su26sd09.entity.ChiTietDatPhong;
import su26sd09.su26sd09.entity.Chi_tiet_dich_vu;
import su26sd09.su26sd09.entity.DatPhong;
import su26sd09.su26sd09.entity.KhuyenMai;
import su26sd09.su26sd09.entity.LoaiPhong;
import su26sd09.su26sd09.entity.LoaiPhongAnh;
import su26sd09.su26sd09.entity.Phong;
import su26sd09.su26sd09.entity.PhongAnh;
import su26sd09.su26sd09.repository.ChiTietDatPhongRepo;
import su26sd09.su26sd09.repository.ChiTietDichvuRepo;
import su26sd09.su26sd09.repository.LoaiPhongAnhRepository;
import su26sd09.su26sd09.repository.LoaiPhongRepository;
import su26sd09.su26sd09.repository.PhongRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Trang quan ly lich dat phong (property management calendar) - dung chung
 * cho ca STAFF va ADMIN. Day la 1 single page application: trang chi render
 * shell 1 lan, phan danh sach LoaiPhong va luoi lich thang duoc nap qua cac
 * API JSON ben duoi bang fetch() tren client.
 */
@Controller
@RequestMapping("/nhan-su/lich-phong")
public class LichPhongController {

    // Anh mac dinh khi loai phong chua co anh dai dien - lay tu bucket /media
    // giong voi cach lam o cac trang khac (VD: rooms.html).
    private static final String ANH_MAC_DINH = "/media/ed2d10ce-680a-467e-a83c-c0781f53a5fd";

    @Autowired
    private LoaiPhongRepository loaiPhongRepo;

    @Autowired
    private PhongRepository phongRepo;

    @Autowired
    private LoaiPhongAnhRepository loaiPhongAnhRepo;

    @Autowired
    private ChiTietDatPhongRepo chiTietDatPhongRepo;

    @Autowired
    private ChiTietDichvuRepo chiTietDichvuRepo;

    @GetMapping
    public String index() {
        return "nhan-vien/lich-phong";
    }

    private String thumbAnhLoaiPhong(LoaiPhong lp) {
        LoaiPhongAnh anhRieng = loaiPhongAnhRepo.findByMaLoaiPhongFirst(lp.getId());
        if (!(anhRieng == null) && anhRieng.getMaAnh() != null) {
            return "/media/" + anhRieng.getMaAnh().getMaAnh();
        }
        return ANH_MAC_DINH;
    }

    @GetMapping("/api/loai-phong")
    @ResponseBody
    public List<LichPhongLoaiPhongDTO> apiLoaiPhong() {
        List<LoaiPhong> all = loaiPhongRepo.findAllByOrderByTenLoaiAsc();
        return all.stream()
                .map(lp -> new LichPhongLoaiPhongDTO(
                        lp.getId(),
                        lp.getTenLoai(),
                        lp.getSucChuaToiDa(),
                        lp.getGiaCoBan(),
                        // Anh duoc phuc vu qua AnhController o /media/{maAnh (UUID)},
                        // KHONG phai ten file tren dia (getSrc()). Lay tu gallery
                        // loai_phong_anh (uploadmulti-anh o trang admin loai phong),
                        // fallback ve anh phong / anh mac dinh neu chua co.
                        thumbAnhLoaiPhong(lp),
                        phongRepo.countByLoaiPhongIdAndHoatDongTrue(lp.getId())
                ))
                .toList();
    }

    @GetMapping("/api/loai-phong/{id}/bookings")
    @ResponseBody
    public List<LichPhongEventDTO> apiBookings(
            @PathVariable("id") int id,
            @RequestParam("start") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam("end") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end
    ) {
        LocalDateTime startDt = start.atStartOfDay();
        LocalDateTime endDt = end.atStartOfDay();

        List<ChiTietDatPhong> rows = chiTietDatPhongRepo.findForCalendar(id, startDt, endDt);

        // Nap dich vu 1 lan cho tat ca cac don dat phong xuat hien tren luoi,
        // tranh N+1 query khi map tung dong.
        Map<Integer, List<LichPhongDichVuDTO>> dichVuTheoDon = new HashMap<>();
        Map<Integer, BigDecimal> tongDichVuTheoDon = new HashMap<>();
        for (ChiTietDatPhong c : rows) {
            int datPhongId = c.getD().getId();
            if (dichVuTheoDon.containsKey(datPhongId)) continue;

            List<Chi_tiet_dich_vu> ctdvList = chiTietDichvuRepo.findByDatPhongId(datPhongId);
            BigDecimal tong = BigDecimal.ZERO;
            List<LichPhongDichVuDTO> dtoList = new ArrayList<>();
            for (Chi_tiet_dich_vu ctdv : ctdvList) {
                int soLuong = ctdv.getSoluong() != null ? ctdv.getSoluong() : 0;
                BigDecimal donGia = ctdv.getDonGia() != null ? ctdv.getDonGia() : BigDecimal.ZERO;
                BigDecimal thanhTien = donGia.multiply(BigDecimal.valueOf(soLuong));
                tong = tong.add(thanhTien);
                dtoList.add(new LichPhongDichVuDTO(
                        ctdv.getDv() != null ? ctdv.getDv().getTen_dich_vu() : "Dịch vụ",
                        ctdv.getDv() != null ? ctdv.getDv().getDonVi() : null,
                        soLuong,
                        donGia,
                        thanhTien,
                        ctdv.getNgay_su_dung(),
                        ctdv.getGhichu()
                ));
            }
            dichVuTheoDon.put(datPhongId, dtoList);
            tongDichVuTheoDon.put(datPhongId, tong);
        }

        return rows.stream()
                .map(c -> {
                    DatPhong d = c.getD();
                    KhuyenMai km = d.getKm();
                    return new LichPhongEventDTO(
                            c.getId(),
                            d.getId(),
                            c.getP().getMaPhong(),
                            c.getP().getSoPhong(),
                            c.getP().getSoTang(),
                            c.getP().getMoTa(),
                            d.getNgaydatPhong(),
                            d.getNgaytraPhong(),
                            d.getTrangThai(),
                            d.getHoten(),
                            d.getSdt(),
                            d.getEmail(),
                            d.ma_cccd,
                            d.getSonguoiLon(),
                            d.getSotreEm(),
                            d.getMaTraCuu(),
                            c.getGiaMoiDem(),
                            c.getGiaKhiDat(),
                            c.getPhuPhi(),
                            d.getNgayTao(),
                            d.getYeuCauThem(),
                            km != null ? km.getPromoCode() : null,
                            km != null ? km.getMoTa() : null,
                            km != null ? km.getLoaiGiam() : null,
                            km != null ? km.getGiatriGiam() : null,
                            dichVuTheoDon.getOrDefault(d.getId(), Collections.emptyList()),
                            tongDichVuTheoDon.getOrDefault(d.getId(), BigDecimal.ZERO)
                    );
                })
                .toList();
    }
}
