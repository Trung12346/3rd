package su26sd09.su26sd09.constants;

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
     * Tập các trạng thái đơn đặt phòng được phép hiển thị trên
     * trang quản lý đơn (admin + nhân viên). Mọi trạng thái nằm ngoài
     * tập này — điển hình là "Chua thanh toan" — sẽ bị ẩn đi.
     */
    public static final Set<String> DP_TRANG_THAI_HIEN_THI = Set.of(
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
            "Cho xac nhan",
            "Da xac nhan",
            "Da nhan phong",
            "Da tra phong",
            "Da huy",
            DP_CHO_HUY
    );
}