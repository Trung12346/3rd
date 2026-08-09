# Cho phép đổi sang mọi phòng & xử lý tiền thừa trên DatPhong

## Context

Hiện tại chức năng "Đổi phòng" trong trang check-in (`/nhan-su/admin/dat-phong/{id}/check-in`) đang có 2 hạn chế:

1. **Template filter chặt**: Controller ở `AdminDatPhongController.java:1106-1108` skip mọi loại phòng có `giaCoBan < gia hien tai`. Kết quả: chỉ hiển thị phòng **cùng loại** hoặc **đắt hơn**.
2. **Không có ô tiền thừa**: Khi đổi sang phòng rẻ hơn → `chenhLechTong < 0` (âm) → tiền hoàn không được ghi nhận đâu cả. Logic cập nhật HoaDon (`AdminDatPhongController.java:347-358`) chỉ cộng `chenhLechTong` vào `tienPhong` + `tongTien` → **không có dòng riêng cho tiền thừa cần hoàn lại khách**.

User yêu cầu:
- Cho phép đổi **tự do sang mọi phòng Trống** (mọi giá, mọi loại).
- Khi đổi → lưu tiền thừa lên **bảng `dat_phong`** (không phải `hoa_don`) để tách bạch với logic hoàn tiền khi hủy phòng.

## Hướng tiếp cận (ĐÃ SỬA)

### 1) DB schema — user đã thêm 2 cột vào `dat_phong`

```sql
ALTER TABLE dat_phong ADD COLUMN
  tien_thua_do_doi_phong DECIMAL(14,2) NULL,   -- dương = hoàn khách, âm = khách nợ
  trang_thai_tien_thua VARCHAR(50) NULL;        -- CHO_HOAN | KHACH_NO_THEM | NULL
```

### 2) Entity `DatPhong.java`

Thêm 2 field tương ứng (`@Column(name = "...")`):
```java
@Column(name = "tien_thua_do_doi_phong", precision = 14, scale = 2)
public java.math.BigDecimal tienThuaDoDoiPhong;

@Column(name = "trang_thai_tien_thua", length = 50)
public String trangThaiTienThua;
```

### 3) Entity `HoaDon.java` — KHÔNG thêm gì

(Giữ nguyên như ban đầu — user đã chọn đặt cột ở DatPhong.)

### 4) Controller — bỏ filter giá/loại

File: `src/main/java/su26sd09/su26sd09/controller/AdminDatPhongController.java`

Trong `buildCheckinChiTiet()` (~dòng 1106-1108):
```java
// XOÁ: BigDecimal giaHienTai = ... + if (lp.getGiaCoBan().compareTo(giaHienTai) < 0) continue;
// THANH: lấy TẤT CẢ loại phòng
for (LoaiPhong lp : phongService.findAllLoai()) { ... }
```
Hiển thị mọi phòng có `trangThai = "Trong"`, kể cả khác loại.

### 5) Controller — cập nhật logic đổi phòng (`doPhong`, ~dòng 196-369)

Sau khi tính `chenhLechTong`:
- Vẫn cộng vào `hoa_don.tienPhong` + `tongTien` (giữ nguyên logic cũ cho hiển thị tổng tiền).
- Thêm phần ghi lên `dat_phong`:
  - `chenhLechTong < 0` (đổi sang rẻ hơn) → `dp.setTienThuaDoDoiPhong(abs)`, `dp.setTrangThaiTienThua("CHO_HOAN")`.
  - `chenhLechTong > 0` (đổi sang đắt hơn) → `dp.setTienThuaDoDoiPhong(negate)`, `dp.setTrangThaiTienThua("KHACH_NO_THEM")`.
  - `chenhLechTong == 0` → reset về null.

Lưu `dp` bằng `datPhongService.save(dp)` ở cuối method (đã có sẵn dòng `datPhong.setNgayCapNhat + datPhongService.save`).

### 6) Template — hiển thị dòng "Chênh lệch do đổi phòng"

File: `src/main/resources/templates/admin/dat-phong-check-in.html`

Trong panel "Tóm tắt đơn phòng" (~dòng 1340-1401), thêm 1 block mới giữa "Dịch vụ sử dụng" và "Áp dụng khuyến mãi". Biến đọc trực tiếp từ `${dp.tienThuaDoDoiPhong}` và `${dp.trangThaiTienThua}` — không cần `model.addAttribute` riêng vì `dp` đã có sẵn trong model.

```html
<div class="summary-block" th:if="${dp.tienThuaDoDoiPhong != null and dp.tienThuaDoDoiPhong.signum() != 0}">
  <p class="summary-label">Chênh lệch do đổi phòng</p>
  <div class="summary-row">
    <span th:text="${dp.tienThuaDoDoiPhong.signum() > 0 ? 'Tiền thừa cần hoàn khách' : 'Khách cần trả thêm'}"></span>
    <span class="gia-chenh-do-doi"
          th:classappend="${dp.tienThuaDoDoiPhong.signum() > 0} ? 'duong' : 'am'"
          th:text="${(dp.tienThuaDoDoiPhong.signum() > 0 ? '+' : '-') + #numbers.formatDecimal(dp.tienThuaDoDoiPhong.abs(), 0, 'COMMA', 0, 'POINT') + ' VND'}"></span>
  </div>
  <div class="summary-row" th:if="${dp.trangThaiTienThua != null}">
    <span>Trạng thái</span>
    <span class="status-badge"
          th:classappend="${dp.trangThaiTienThua == 'CHO_HOAN'} ? 'warning' : 'danger'"
          th:text="${dp.trangThaiTienThua == 'CHO_HOAN' ? 'Chờ hoàn' : 'Khách nợ thêm'}"></span>
  </div>
</div>
```

### 7) CSS — style dòng tiền thừa

```css
.gia-chenh-do-doi { font-weight: 600; }
.gia-chenh-do-doi.duong { color: #16a34a; }
.gia-chenh-do-doi.am    { color: #dc2626; }
```

## Critical files

1. ✅ `src/main/java/su26sd09/su26sd09/entity/DatPhong.java` — đã thêm 2 field.
2. ✅ `src/main/java/su26sd09/su26sd09/entity/HoaDon.java` — đã hoàn tác (xóa 3 field).
3. ✅ `src/main/java/su26sd09/su26sd09/controller/AdminDatPhongController.java`:
   - `buildCheckinChiTiet()`: đã bỏ filter giá (~dòng 1106-1108).
   - `doPhong()`: đã set `tienThuaDoDoiPhong` + `trangThaiTienThua` lên `datPhong`.
4. ✅ `src/main/resources/templates/admin/dat-phong-check-in.html`:
   - Đã thêm block "Chênh lệch do đổi phòng" trong panel tóm tắt.
   - Đã thêm CSS.
5. ✅ SQL: user đã thêm 2 cột vào `dat_phong`.

## Verification

1. **Compile** project trong IDE — không lỗi.
2. **Reload trang check-in** ở đơn bất kỳ.
3. **Test case 1 - đổi sang phòng đắt hơn**:
   - Phần "Phòng có thể đổi" hiển thị **tất cả phòng Trống** kể cả khác loại.
   - Chọn phòng đắt hơn → xác nhận → reload → panel tóm tắt có dòng "Khách cần trả thêm" màu đỏ, badge "Khách nợ thêm".
4. **Test case 2 - đổi sang phòng rẻ hơn** (khác loại):
   - Chọn phòng Standard (~1tr) thay cho Presidential (5tr).
   - Xác nhận → reload → panel tóm tắt có dòng "Tiền thừa cần hoàn khách" màu xanh lá, badge "Chờ hoàn".
5. **Test case 3 - DB check**:
   - `SELECT tien_thua_do_doi_phong, trang_thai_tien_thua FROM dat_phong WHERE ma_dat_phong = ?;`
   - Kết quả phải khớp với UI hiển thị.
6. **Test case 4 - hóa đơn PDF**:
   - Hóa đơn đã xuất PDF → vẫn bị chặn đổi như cũ (`hoaDonService.isDaXuat()`).
