# Plan: Redesign trang Check-out — lịch ngày trả + tổng hợp đơn chi tiết

## 1. Context

Hiện tại trang `/nhan-su/checkout` (checkout-list.html) là 1 danh sách đơn giản — gần giống `/nhan-su/dat-phong` và không có ý nghĩa rà soát. Khi nhân viên muốn xử lý check-out, họ cần:
1. **Chọn nhanh đơn cần trả** theo ngày (hôm nay + các ngày sắp tới), giống như trang check-in đang làm với ngày nhận.
2. **Tổng hợp toàn bộ thông tin đơn** trước khi chốt: khách, phòng, dịch vụ, khuyến mãi, lịch sử thanh toán, số dư, phụ phí trả muộn...

Yêu cầu của user:
- **Bỏ hẳn** trang `/nhan-su/checkout` (list rỗng, vô dụng).
- Thiết kế lại `/nhan-su/checkout/{id}` thành 1 **trang tổng hợp** có **lịch ngày trả ở trên đầu** + **danh sách đơn sắp check-out** (theo ngày đang chọn hoặc hôm nay) + **panel chi tiết đơn đang xem** (nếu có ?id).

Tận dụng pattern giống `checkinDp` + `buildCheckinList` của `NhanVienDatPhongController` nhưng lọc theo `ngaytraPhong` thay vì `ngaydatPhong`.

---

## 2. UI đề xuất

### Trang `/nhan-su/checkout/{id}` (mới — gộp list + detail)

```
+------------------------------------------------------------+
| TRẢ PHÒNG (CHECKOUT)              [X đơn đang xem]        |
+------------------------------------------------------------+
| [search-pill: tìm tên/SĐT/mã đơn]                         |
| [chip: Xem theo tháng MM/yyyy] [chip: Theo khoảng ngày]    |
+------------------------------------------------------------+
| DẢI NGÀY TRONG THÁNG:                                    |
|  [< Tháng trước] [Hôm nay] [Tháng sau >]                  |
|  ┌──┬──┬──┬──┬──┬──┐                                      |
|  │ 1│ 2│ 3│...│30│31│  (có chấm tròn nếu ngày có đơn)   |
|  └──┴──┴──┴──┴──┴──┘                                      |
+------------------------------------------------------------+
| DANH SÁCH ĐƠN SẮP TRẢ TRONG KHOẢNG:                      |
| ┌─────────────────────────────────────────────────────┐   |
| │ #42 — Nguyen Van A | P101, P102 | Trả 12/08 14h    │   |
| │ ───────────────────────────────────── Đã nhận phòng  │   |
| │ #43 — Tran Thi B  | P201      | Trả 13/08 11h    │   |
| │ ───────────────────────────────────── Đã trả phòng  │   |
| └─────────────────────────────────────────────────────┘   |
+------------------------------------------------------------+
| HINT BANNER khi chưa chọn đơn:                            |
| "Chọn một đơn phòng từ danh sách phía trên để rà soát."  |
+------------------------------------------------------------+
| PHẦN CHI TIẾT (chỉ hiện khi có ?id=X):                   |
| ┌─────────────────────────┬─────────────────────────┐    |
| │ PANEL 1: Thông tin đơn  │ PANEL TÓM TẮT BÊN PHẢI │    |
| │ - Khách, CCCD, SĐT, mail│ - Số đêm, khách         │    |
| │ - Ngày nhận, ngày trả    │ - Tiền phòng, dịch vụ   │    |
| │ - Mã tra cứu             │ - Khuyến mãi (nếu có)  │    |
| │ - Nhân viên xử lý       │ - Phụ phí trả muộn     │    |
| │ - Yêu cầu thêm          │ - VAT, Tổng cộng        │    |
| │                         │ - Đã thanh toán, hoàn   │    |
| ├─────────────────────────┤ - SỐ DƯ (nợ / thừa)    │    |
| │ PANEL 2: Phòng (bảng)  ├─────────────────────────┤    |
| │ số phòng | tầng | loại  │ PANEL 3: BANNER 3-CASE │    |
| │ CCCD khách ở | giá     │ đủ / còn nợ / thừa      │    |
| ├─────────────────────────┼─────────────────────────┤    |
| │ PANEL 4: Dịch vụ       │ PANEL 5: LỊCH SỬ TT    │    |
| │ (bảng)                  │ Ngày | PT | Số tiền    │    |
| │ + Form thêm DV phát sinh│ | Ghi chú              │    |
| ├─────────────────────────┼─────────────────────────┤    |
| │ PANEL 6: Hóa đơn       │ PANEL 7: HOÀN TIỀN    │    |
| │ folio đầy đủ            │ (nếu có yêu cầu)        │    |
| │ Tiền phòng              ├─────────────────────────┤    |
| │ Tiền DV                  │ PANEL 8: ACTIONS       │    |
| │ Giảm giá (KM)           │ [Thu Tiền] (nếu nợ)    │    |
| │ Phụ phí trả muộn        │ [Ghi nhận Hoàn]        │    |
| │ VAT                      │ [Chốt Trả Phòng]       │    |
| │ Tổng cộng                │   (disable nếu lệch)   │    |
| │ Đã thanh toán            │                          │    |
| │ SỐ DƯ                    │ Sau chốt:               │    |
| │                          │ [Xuất Hóa Đơn PDF]      │    |
| └─────────────────────────┴─────────────────────────┘    |
+------------------------------------------------------------+
```

### Đặc điểm
- **Lịch ngày trả**: filter theo `ngaytraPhong` (KHÔNG phải `ngaydatPhong` như check-in). Mỗi ô ngày có chấm tròn nếu có ≥1 đơn sắp trả.
- **Danh sách đơn**: sort theo `ngaytraPhong` ASC, đơn hôm nay lên đầu.
- **Mỗi đơn trong danh sách** show: mã, khách, phòng, ngày giờ trả, badge trạng thái (Đang lưu trú / Đã trả phòng).
- **Click 1 đơn** → `?id=X` → cuộn xuống panel chi tiết.
- **Panel chi tiết** dùng layout 2 cột (giống check-in): trái là thông tin/phòng/DV, phải là tóm tắt + actions.

---

## 3. Controller flow

### 3.1 Đổi route `/nhan-su/checkout`

**Trước**:
- `GET /nhan-su/checkout` → `danhSach()` (list cũ, trống)
- `GET /nhan-su/checkout/{id}` → `chiTiet()` (detail)

**Sau**:
- `GET /nhan-su/checkout` → **redirect** sang `/nhan-su/checkout/today` (hoặc `/nhan-su/checkout/{id}` nếu có param `?id=`)
- `GET /nhan-su/checkout/{id}` (id có thể là số đơn hoặc "today" / "hom-nay") → method mới `checkoutDp()`:
  - Nếu `id` là số: hiển thị detail + gọi `buildCheckoutList()` cho phần trên.
  - Nếu `id` là "today" (hoặc 0/null): chỉ hiển thị list + dải ngày, không có detail.

### 3.2 Method mới `checkoutDp()`

```java
@GetMapping({"/checkout/{id}", "/checkout"})
public String checkoutDp(@PathVariable(value = "id", required = false) String idRaw,
                          @RequestParam(value = "ngay", required = false)
                          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ngayChon,
                          @RequestParam(value = "thang", required = false) String thangRaw,
                          @RequestParam(value = "q", required = false) String q,
                          @RequestParam(value = "tuNgay", required = false) String tuNgayRaw,
                          @RequestParam(value = "denNgay", required = false) String denNgayRaw,
                          Model model, RedirectAttributes redirectAttributes) {

    // Resolve idRaw → Integer maDon (null nếu không phải số / = "today" / "hom-nay")
    Integer maDon = parseIdParam(idRaw);

    // ...check quyền coQuyenCheckout()...

    buildCheckoutList(model, ngayChon, q, tuNgayRaw, denNgayRaw);
    if (maDon != null) {
        DatPhong dp = datPhongService.findById(maDon);
        if (dp == null) { ...flash error... return "redirect:/nhan-su/checkout/today"; }
        buildCheckoutChiTiet(dp, model);  // dùng napModelChiTiet đã có
    }

    return "nhan-vien/checkout-chi-tiet";  // template MỚI (dưới)
}
```

### 3.3 Method `buildCheckoutList()` (mới)

Tương tự `buildCheckinList()` nhưng:
- Lọc đơn có `ngaytraPhong` ∈ khoảng [tuNgay, denNgay]
- Filter trạng thái: `"Da nhan phong"` (chưa trả) hoặc `"Da tra phong"` (đã trả, để xem hóa đơn)
- Sort theo `ngaytraPhong` ASC
- Mỗi ngày trong tháng đếm đơn có `ngaytraPhong` = ngày đó

### 3.4 Method `parseIdParam()` + `buildCheckoutChiTiet()`

- `parseIdParam("today")` / `parseIdParam("hom-nay")` / `parseIdParam(null)` → return null
- `parseIdParam("42")` → return 42
- `buildCheckoutChiTiet(dp, model)` — gọi lại `napModelChiTiet(dp, model)` đã có sẵn + thêm `lichSuThanhToan` (gọi `thanhToanRepo.findByH_IdOrderByNgaythanhToanAsc(hoaDon.getId())` nếu có HoaDon).

### 3.5 Redirect các chỗ đang dùng URL cũ

Cần đổi các chỗ trỏ về `/nhan-su/checkout` (list cũ):
- Trong `checkout-chi-tiet.html`: `Quay Lại Danh Sách` → đổi thành `Quay Lại` (bỏ link)
- Trong `fragments/cms-sidebar.html` đã sửa ở turn trước: link `/nhan-su/checkout` → `/nhan-su/checkout/today`

---

## 4. Danh sách file cần sửa

| File | Mục đích sửa |
|------|--------------|
| `NhanVienCheckoutController.java` | Thêm `checkoutDp()`, `buildCheckoutList()`, `buildCheckoutChiTiet()`, `parseIdParam()`. Đổi route cũ `danhSach()` → redirect. Tận dụng `parseThangParam` + `thangLienKe` + `napModelChiTiet` có sẵn. |
| `checkout-chi-tiet.html` | **Viết lại hoàn toàn**: gộp list (lịch + danh sách) + chi tiết đơn. Copy CSS từ `check-in.html` (`.checkin-list-block`, `.ngay-strip`, `.don-checkin-row`, `.summary-panel`, `.status-badge`, v.v.) — chỉnh sửa nhỏ cho phù hợp check-out. |
| `fragments/cms-sidebar.html` | Đổi link menu từ `/nhan-su/checkout` → `/nhan-su/checkout/today`. |
| `NhanVienCheckoutController.java` — bỏ method `chiTiet()` cũ | Thay bằng logic mới trong `checkoutDp()`. |

**Không cần sửa**: entity, security config, các controller khác.

---

## 5. Helper / service dùng lại

- `NhanVienCheckoutController.coQuyenCheckout()` (đã sửa ở turn trước)
- `NhanVienCheckoutController.napModelChiTiet()` (đã có, bổ sung `lichSuThanhToan`)
- `NhanVienDatPhongController.parseThangParam()`, `thangLienKe()` — copy pattern vào `NhanVienCheckoutController` (vì nằm controller khác, không gọi được).
- `DatPhongService.findAll()`, `findById()`, `findPhongByDatPhongId()`
- `ChiTietDatPhongService.findByDatPhongId()`
- `ChiTietDichVuService.findByDatPhongId()`
- `HoaDonService.findByDatPhongId()`, `saveWithPaymentStatusCheck()`
- `ThanhToanRepo.findByH_IdOrderByNgaythanhToanAsc()` (đã inject)
- `DichVuService.findAll()`

---

## 6. Verification (end-to-end)

1. **Vào `/nhan-su/checkout`** → tự redirect sang `/nhan-su/checkout/today`. Hiển thị lịch tháng hiện tại + danh sách đơn sắp trả trong tháng.
2. **Click 1 ô ngày** trên dải ngày → URL `?ngay=YYYY-MM-DD` → danh sách lọc theo ngày đó.
3. **Bấm nút tháng trước** → URL `?thang=YYYY-MM` → lịch chuyển sang tháng trước.
4. **Click 1 đơn trong danh sách** → URL `?id=X` (hoặc `/nhan-su/checkout/{id}`) → cuộn xuống panel chi tiết với đầy đủ thông tin: khách, phòng, dịch vụ, lịch sử thanh toán, tóm tắt, banner 3-case, các nút thu/hoàn/chốt.
5. **Bấm "Chốt Trả Phòng"** khi số dư = 0 → đổi trạng thái đơn + giải phóng phòng → hiển thị nút "Xuất PDF".
6. **Bấm "Đã trả phòng" ở dat-phong-list** → redirect sang `/nhan-su/checkout/{id}` (đã có từ turn trước).
7. **Click menu sidebar "Trả phòng (Checkout)"** → vào `/nhan-su/checkout/today`.

---

### Critical Files for Implementation
- `C:\Users\ADMIN\Downloads\3rd\src\main\java\su26sd09\su26sd09\controller\NhanVienCheckoutController.java`
- `C:\Users\ADMIN\Downloads\3rd\src\main\resources\templates\nhan-vien\checkout-chi-tiet.html` (viết lại)
- `C:\Users\ADMIN\Downloads\3rd\src\main\resources\templates\fragments\cms-sidebar.html` (cập nhật link menu)
- `C:\Users\ADMIN\Downloads\3rd\src\main\resources\templates\nhan-vien\checkout-list.html` (xóa hoặc giữ làm fallback — không dùng nữa)