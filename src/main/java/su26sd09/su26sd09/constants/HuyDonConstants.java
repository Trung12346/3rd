package su26sd09.su26sd09.constants;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

public class HuyDonConstants {
    public static final String TT_HOAN_CHO_XU_LY = "Cho xu ly";
    public static final String TT_HOAN_DA_HOAN   = "Da hoan";
    public static final String TT_HOAN_TU_CHOI   = "Tu choi";

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
     * Khác {@link #DP_TRANG_THAI_HIEN_THI} ở chỗ LOẠI TRỪ "Yeu cau dat phong" —
     * các đơn này thuộc về trang quản lý yêu cầu đặt phòng riêng.
     */
    public static final Set<String> DP_TRANG_THAI_HIEN_THI_BOOKING_MGMT = Set.of(
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