package su26sd09.su26sd09.constants;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

public class HuyDonConstants {
    public static final String TT_HOAN_CHO_XU_LY = "Cho xu ly";
    public static final String TT_HOAN_DA_HOAN   = "Da hoan";
    public static final String TT_HOAN_TU_CHOI   = "Tu choi";
    // Trạng thái hóa đơn khi NV/Admin xác nhận hủy đơn nhưng KHÔNG hoàn tiền
    // (vì tỷ lệ hoàn = 0% theo rule, hoặc NV/Admin chọn hủy không hoàn).
    // Đơn vẫn chuyển sang "Da huy", hóa đơn vẫn được phép xuất PDF để khách cầm về,
    // số tiền hoàn = 0 VND, lý do hủy được lưu vào ghiChu hóa đơn.
    public static final String TT_HOAN_HUY_KHONG_HOAN = "Huy khong hoan";

    public static final String LOAI_GD_THU  = "Thu tien";
    public static final String LOAI_GD_HOAN = "Hoan tien";

    public static final String PT_TIEN_MAT = "Tien Mat";
    public static final String PT_CHUYEN_KHOAN = "Chuyen Khoan";

    // Trạng thái đơn đặt phòng khi vừa yêu cầu hủy, chờ admin xác nhận hoàn tiền
    public static final String DP_CHO_HUY = "Cho huy";

    // Trạng thái "đơn mồ côi" — tạo từ giỏ hàng nhưng chưa thanh toán.
    // Các trang quản lý đơn đặt phòng của admin/nhân viên sẽ ẩn trạng thái này.
    public static final String DP_CHUA_THANH_TOAN = "Chua thanh toan";

    /**
     * Trạng thái đơn đặt phòng khi khách online vừa gửi yêu cầu — chưa qua xác nhận của nhân viên.
     * Phòng đã được hệ thống tự chọn (assignRoomsForType) nhưng chỉ là "giữ chỗ tạm",
     * nhân viên cần vào trang quản lý yêu cầu để xác nhận trước khi đơn đi vào luồng thanh toán.
     */
    public static final String DP_YEU_CAU_DAT_PHONG = "Yeu cau dat phong";

    /** Trạng thái đơn đặt phòng khi đã được NV xác nhận yêu cầu, đang chờ khách thanh toán. */
    public static final String DP_CHO_XAC_NHAN = "Cho xac nhan";

    /**
     * Trạng thái đơn đặt phòng khi đã được thanh toán ĐỦ (100%).
     * KHÔNG còn do nhân viên bấm xác nhận thủ công quyết định — mọi nơi cập nhật
     * HoaDon.daThanhToan qua {@code HoaDonService#saveWithPaymentStatusCheck} sẽ tự
     * động đẩy đơn từ "Yeu cau dat phong" / "Cho xac nhan" sang trạng thái này khi
     * hóa đơn được thanh toán đủ.
     */
    public static final String DP_DA_XAC_NHAN = "Da xac nhan";

    /**
     * Tập trạng thái "chưa xác nhận" — được phép tự động chuyển sang {@link #DP_DA_XAC_NHAN}
     * khi hóa đơn tương ứng được thanh toán đủ. Các trạng thái hạ nguồn hơn (đã nhận phòng,
     * đã trả phòng, đã hủy...) không bao giờ bị ghi đè bởi luồng thanh toán.
     */
    public static final Set<String> DP_TRANG_THAI_CHO_THANH_TOAN_TU_DONG = Set.of(
            DP_YEU_CAU_DAT_PHONG,
            DP_CHO_XAC_NHAN
    );

    /**
     * Số giờ tối đa 1 đơn được phép ở trạng thái "Yeu cau dat phong" kể từ lúc tạo
     * (ngayTao) trước khi bị dọn rác tự động nếu khách không hoàn tất thanh toán/
     * không được nhân viên xử lý.
     */
    public static final long YEU_CAU_DAT_PHONG_HET_HAN_GIO = 24L;

    /**
     * Giờ chốt (giờ trong ngày) của chính sách "quá hạn nhận phòng": nếu đã qua
     * 12:00 của ngày SAU ngày nhận phòng (ngaydatPhong) mà đơn vẫn ở trạng thái
     * "Yeu cau dat phong", đơn bị dọn rác tự động dù chưa đủ 24h kể từ lúc tạo.
     */
    public static final int YEU_CAU_DAT_PHONG_GIO_CHOT_QUA_HAN = 12;

    /**
     * Tập các trạng thái đơn đặt phòng được phép hiển thị trên
     * trang quản lý đơn (admin + nhân viên). Mọi trạng thái nằm ngoài
     * tập này — điển hình là "Chua thanh toan" — sẽ bị ẩn đi.
     */
    public static final Set<String> DP_TRANG_THAI_HIEN_THI = Set.of(
            DP_YEU_CAU_DAT_PHONG,
            "Cho xac nhan",
            "Da xac nhan",
            "Da nhan phong",
            "Da tra phong",
            "Da huy",
            DP_CHO_HUY
    );

    /**
     * Danh sách hiển thị (giữ thứ tự) tương ứng {@link #DP_TRANG_THAI_HIEN_THI},
     * dùng cho filter lọc trong repository / service.
     */
    public static final List<String> DP_TRANG_THAI_HIEN_THI_LIST = List.of(
            DP_YEU_CAU_DAT_PHONG,
            "Cho xac nhan",
            "Da xac nhan",
            "Da nhan phong",
            "Da tra phong",
            "Da huy",
            DP_CHO_HUY
    );

    /**
     * Set trạng thái hiển thị trên trang QUẢN LÝ ĐƠN (admin/nhan-vien).
     * Nay BAO GỒM "Yeu cau dat phong" — đơn ở trạng thái này đã giữ chỗ
     * (khóa lịch) nên được coi là một đơn đặt phòng thực sự và phải xuất
     * hiện trên trang quản lý đơn, không chỉ ở trang quản lý yêu cầu riêng.
     */
    public static final Set<String> DP_TRANG_THAI_HIEN_THI_BOOKING_MGMT = Set.of(
            DP_YEU_CAU_DAT_PHONG,
            "Cho xac nhan",
            "Da xac nhan",
            "Da nhan phong",
            "Da tra phong",
            "Da huy",
            DP_CHO_HUY
    );

    /**
     * Trạng thái "đơn đã chốt nhưng khách chưa đến nhận phòng".
     * Dùng cho toast cảnh báo trễ trên trang quản lý đơn (không tự hủy).
     */
    public static final Set<String> DP_TRANG_THAI_CHUA_NHAN_PHONG = Set.of(
            DP_YEU_CAU_DAT_PHONG,
            "Cho xac nhan",
            "Da xac nhan"
    );

    /** Phụ phí check-in trễ (VND), cố định theo yêu cầu hiện tại. */
    public static final BigDecimal PHU_PHI_CHECKIN_TRE_VND = new BigDecimal("100000");

    /** Ngưỡng phụ phí check-in trễ: khách trễ ≥ 60 phút mới thu phụ phí. */
    public static final long PHU_PHI_CHECKIN_TRE_SOPHUT_TOI_THIEU = 60L;

    /** Ngưỡng cảnh báo trễ: đơn đã qua giờ nhận > 1 ngày mới hiện toast. */
    public static final long CANH_BAO_TRE_SONGAY = 1L;
}