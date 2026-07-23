package su26sd09.su26sd09.dto;

/**
 * Cho biết khách hàng hiện tại có được viết đánh giá cho 1 phòng hay không.
 * Quy tắc: chỉ được đánh giá cho lượt đặt phòng (dat_phong) GẦN NHẤT của
 * khách hàng đó với phòng này, và mỗi lượt đặt phòng chỉ được đánh giá 1 lần.
 */
public class RoomReviewEligibilityDTO {

    private final boolean canReview;
    private final String reason;
    private final Integer maDatPhong; // lượt đặt phòng gần nhất đủ điều kiện (null nếu không đủ điều kiện)

    private RoomReviewEligibilityDTO(boolean canReview, String reason, Integer maDatPhong) {
        this.canReview = canReview;
        this.reason = reason;
        this.maDatPhong = maDatPhong;
    }

    public static RoomReviewEligibilityDTO enabled(Integer maDatPhong) {
        return new RoomReviewEligibilityDTO(true, null, maDatPhong);
    }

    public static RoomReviewEligibilityDTO disabled(String reason) {
        return new RoomReviewEligibilityDTO(false, reason, null);
    }

    public boolean isCanReview() {
        return canReview;
    }

    public String getReason() {
        return reason;
    }

    public Integer getMaDatPhong() {
        return maDatPhong;
    }
}
