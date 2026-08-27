package su26sd09.su26sd09.constants;

import java.util.List;

/**
 * Danh muc loai hanh dong / doi tuong dung cho bang lich_su_hoat_dong
 * (nhat ky hoat dong cua nhan su). Tap trung khai bao tai day de moi noi
 * ghi log deu dung chung 1 bo gia tri, tranh sai chinh ta lam loc bo filter.
 */
public class LichSuHoatDongConstants {

    // ===== loai_hanh_dong =====
    public static final String HD_CHECK_IN = "CHECK_IN";
    public static final String HD_CHECK_OUT = "CHECK_OUT";
    public static final String HD_HOAN_TIEN = "HOAN_TIEN";
    public static final String HD_THU_TIEN = "THU_TIEN";
    public static final String HD_TAO_DAT_PHONG = "TAO_DAT_PHONG";
    public static final String HD_HUY_DAT_PHONG = "HUY_DAT_PHONG";
    public static final String HD_XAC_NHAN_YEU_CAU = "XAC_NHAN_YEU_CAU_DAT_PHONG";
    public static final String HD_CAP_NHAT_DAT_PHONG = "CAP_NHAT_DAT_PHONG";
    public static final String HD_XAC_NHAN_HOAN_TIEN = "XAC_NHAN_HOAN_TIEN";
    public static final String HD_TU_CHOI_HOAN_TIEN = "TU_CHOI_HOAN_TIEN";
    public static final String HD_HUY_KHONG_HOAN = "HUY_KHONG_HOAN";
    public static final String HD_DAT_PHONG_TAI_QUAY = "DAT_PHONG_TAI_QUAY";

    public static final List<String> TAT_CA_LOAI_HANH_DONG = List.of(
            HD_CHECK_IN,
            HD_CHECK_OUT,
            HD_HOAN_TIEN,
            HD_THU_TIEN,
            HD_TAO_DAT_PHONG,
            HD_HUY_DAT_PHONG,
            HD_XAC_NHAN_YEU_CAU,
            HD_CAP_NHAT_DAT_PHONG,
            HD_XAC_NHAN_HOAN_TIEN,
            HD_TU_CHOI_HOAN_TIEN,
            HD_HUY_KHONG_HOAN,
            HD_DAT_PHONG_TAI_QUAY
    );

    // ===== doi_tuong =====
    public static final String DT_DAT_PHONG = "DatPhong";
    public static final String DT_HOA_DON = "HoaDon";
    public static final String DT_THANH_TOAN = "ThanhToan";
    public static final String DT_PHONG = "Phong";

    private LichSuHoatDongConstants() {
    }
}
