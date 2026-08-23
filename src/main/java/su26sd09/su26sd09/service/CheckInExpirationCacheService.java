package su26sd09.su26sd09.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import su26sd09.su26sd09.entity.DatPhong;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Quan ly han check-in (thoi diem toi han khach phai check-in truoc khi bi coi
 * la "Khach vang") cho tung don dat phong.
 *
 * Mac dinh (chinh sach no-show): 12:00 cua ngay SAU ngay_nhan_phong.
 * Nhan vien co the GIA HAN moc nay (vd: khach goi dien xin den tre) qua trang
 * "Xem don hien tai" o so-do-phong -> ghi de vao cache nay.
 *
 * Cache duoc luu ra 1 file JSON o project root de:
 *  - Khong can them bang/migration moi trong DB cho 1 tinh nang "mem".
 *  - Song duoc qua lan restart server (doc lai luc khoi dong).
 *
 * Key: ma_dat_phong (String hoa cho de doc/debug JSON).
 * Value: han check-in dang ghi de, dinh dang ISO_LOCAL_DATE_TIME (vd 2026-08-25T12:00:00).
 * Don nao KHONG co trong file -> dung han mac dinh tinh tu ngaydatPhong.
 */
@Service
public class CheckInExpirationCacheService {

    /** File cache dat o project root (thu muc lam viec khi chay app). */
    private static final String CACHE_FILE_NAME = "checkin-expiration-cache.json";

    /** Gio chot cua chinh sach no-show mac dinh: 12:00 ngay hom sau ngay nhan phong. */
    public static final int GIO_CHOT_MAC_DINH = 12;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final File cacheFile = new File(CACHE_FILE_NAME);

    // ma_dat_phong (String) -> han check-in (chuoi ISO_LOCAL_DATE_TIME)
    private final Map<String, String> overrides = new ConcurrentHashMap<>();

    @PostConstruct
    public synchronized void load() {
        if (!cacheFile.exists()) {
            return;
        }
        try {
            byte[] bytes = Files.readAllBytes(cacheFile.toPath());
            if (bytes.length == 0) return;
            Map<String, String> tu = objectMapper.readValue(bytes, Map.class);
            overrides.clear();
            overrides.putAll(tu);
        } catch (IOException e) {
            System.out.println("[CheckInExpirationCache] Khong doc duoc file cache, bo qua: " + e.getMessage());
        }
    }

    private synchronized void ghiFile() {
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(cacheFile, overrides);
        } catch (IOException e) {
            System.out.println("[CheckInExpirationCache] Khong ghi duoc file cache: " + e.getMessage());
        }
    }

    /** Han check-in mac dinh theo chinh sach no-show: 12:00 ngay hom sau ngay_nhan_phong. */
    public LocalDateTime hanMacDinh(DatPhong dp) {
        if (dp == null || dp.getNgaydatPhong() == null) return null;
        return dp.getNgaydatPhong().toLocalDate().plusDays(1).atTime(GIO_CHOT_MAC_DINH, 0);
    }

    /** Han check-in dang co hieu luc cho 1 don: uu tien gia han (neu co), khong thi dung mac dinh. */
    public LocalDateTime hanHieuLuc(DatPhong dp) {
        if (dp == null) return null;
        String override = overrides.get(String.valueOf(dp.getId()));
        if (override != null) {
            try {
                return LocalDateTime.parse(override, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            } catch (Exception e) {
                // Du lieu cache loi -> bo qua, fallback mac dinh
            }
        }
        return hanMacDinh(dp);
    }

    /** True neu don dang co 1 moc gia han rieng (khac mac dinh) do nhan vien thiet lap. */
    public boolean coGiaHan(Integer maDatPhong) {
        return maDatPhong != null && overrides.containsKey(String.valueOf(maDatPhong));
    }

    /**
     * Nhan vien gia han moc check-in cho 1 don (vd: khach goi dien xin den tre).
     * Ghi de vao cache va luu ngay xuong file.
     */
    public synchronized void giaHan(Integer maDatPhong, LocalDateTime hanMoi) {
        if (maDatPhong == null || hanMoi == null) return;
        overrides.put(String.valueOf(maDatPhong), hanMoi.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        ghiFile();
    }

    /** Xoa gia han rieng cua 1 don (quay ve dung han mac dinh). */
    public synchronized void xoaGiaHan(Integer maDatPhong) {
        if (maDatPhong == null) return;
        if (overrides.remove(String.valueOf(maDatPhong)) != null) {
            ghiFile();
        }
    }

    /**
     * Don da duoc xu ly xong (check-in / check-out / huy...) -> khong con can theo doi
     * han check-in nua, don gian vet cache cho gon file.
     */
    public synchronized void xoaKhoiTheoDoi(Integer maDatPhong) {
        xoaGiaHan(maDatPhong);
    }

    /**
     * Don gian file cache: chi giu lai entry cua nhung ma_dat_phong con trong danh sach
     * dang duoc theo doi (con "cho check-in"). Goi tu scheduler moi lan quet de file
     * khong phinh to theo thoi gian voi cac don da xu ly xong tu lau.
     */
    public synchronized void donDep(java.util.Set<Integer> maDangTheoDoi) {
        java.util.Set<String> giu = new java.util.HashSet<>();
        for (Integer id : maDangTheoDoi) giu.add(String.valueOf(id));
        boolean thayDoi = overrides.keySet().retainAll(giu);
        if (thayDoi) ghiFile();
    }
}
