# AGENTS.md

This file provides guidance to Codex (Codex.ai/code) when working with code in this repository.

## Project Overview

A hotel management web application (`quan_ly_khach_san`) built with Spring Boot 4.0.6 + Thymeleaf + JPA + SQL Server. Supports three roles: ADMIN, STAFF, and customer (guests). Includes room booking, payment (VNPay sandbox), email verification, reviews, and a revenue statistics dashboard.

## Build & Run Commands

- **Maven wrapper is included.** Use it everywhere — do not assume a global `mvn`.
- Run app: `./mvnw spring-boot:run` (Windows: `mvnw.cmd spring-boot:run`)
- Build JAR: `./mvnw clean package`
- Run tests: `./mvnw test`
- Run a single test class: `./mvnw test -Dtest=ClassName`
- Run a single test method: `./mvnw test -Dtest=ClassName#methodName`
- Java 21 required (`<java.version>21</java.version>` in pom.xml).

## Required Local Configuration

The app will not start without these environment variables and a running SQL Server:

1. **SQL Server** on `localhost:1433` with database `quan_ly_khach_san`. `spring.jpa.hibernate.ddl-auto=none` — schema is expected to exist already (use the team's SQL script).
2. **Env vars for mail** (referenced in `application.properties`):
   - `CLIENT_USERNAME` — Gmail address
   - `CLIENT_PASSKEY` — Gmail app password
3. **VNPay credentials** are hardcoded in `src/main/java/su26sd09/su26sd09/config/vnpayConfig.java` (sandbox tmn code + hash secret). They are public sandbox values but treated as config — update if switching environments.

## Architecture

### Package layout (`src/main/java/su26sd09/su26sd09/`)

- **`controller/`** — One `@Controller` per business area, all using Thymeleaf view names. Naming pattern: `Admin*` (admin views, gated by `ROLE_ADMIN`), `NhanVienDatPhongController` (staff counter bookings, gated by `ROLE_STAFF`/`ROLE_ADMIN`), public controllers (`Home`, `PhongController`, `GioHangController`, `ThanhToanController`, `VnpayController`, `LoginController`, `RegisterController`, `VerifyEmailController`, `LogoutController`, `DanhGiaController`, `UserProfilesController`, `LoaiPhongController`).
- **`service/`** — Business logic, one per entity. `CustomerUserDetailsService` is the Spring Security user details impl.
- **`repository/`** — Spring Data JPA interfaces (`*Repo.java` / `*Repository.java` — naming is mixed, see file listing).
- **`entity/`** — JPA `@Entity` classes mapped to snake_case tables via `@Table(name=...)`. Lombok (`@Getter @Setter @NoArgsConstructor @AllArgsConstructor`) is used everywhere. Some entities have composite-key ID classes (e.g. `TienNghiPhong` + `TienNghiPhongId`).
- **`dto/`** — View/payload DTOs for controllers. `AdvancedThongKeDTO` and `DoanhThuChartDTO` carry statistics responses; `*ReviewRequest` / `*ReviewViewDTO` carry review data; `RegisterDTO`, `NguoiDungDTO` for forms.
- **`config/`** — `SecurityConfig` (form login + role-based URL matching; `/admin/**` requires `ROLE_ADMIN`, `/Nhan-vien/**` requires `ROLE_STAFF`, `/admin/dat-phong-quay/**` requires either), `vnpayConfig` (VNPay signing helpers), `XoaDatPhongConfigSchedule` (cron cleanup of unpaid bookings older than 15 min — see Scheduling below).

### View layout (`src/main/resources/templates/`)

- Root templates: `index.html`, `login.html`, `register.html`, `rooms.html`, `loai-phong.html`, `phong-theo-loai.html`, `room-detail.html`, `gio-hang.html`, `Thanh-Toan.html`, `thanh-toan-thanh-cong.html`, `dat-phong-*` flow, `customer-*` user pages.
- `admin/` — Admin-only list/CRUD views (phong, loai-phong, khach-hang, nhan-vien, nguoi-dung, dat-phong, hoa-don, khuyen-mai, danh-gia, thong-ke).
- `nhan-vien/` — Staff-only views (counter booking `dat-phong-quay.html`, hoa-don, dich-vu, etc.).
- `fragments/headers/` — Per-role header fragments (`admin-header.html`, `staff-header.html`, `customer-header.html`, `guest-header.html`, `app-header.html`, `_header-style.html`). Include the right one based on the user's role when adding new pages.
- `fragments/` — Reusable partials (`customer-reviews.html`, `room-reviews.html`).
- Static assets in `src/main/resources/static/`.

### Scheduling

`@EnableScheduling` is on the main application class. `XoaDatPhongConfigSchedule` runs every 5 minutes, deletes `DatPhong` rows with `trang_thai = "Chua thanh toan"` created more than 15 minutes ago, and rolls room status back to `Trong`. Any new scheduled job should follow the same `@Component` + `@Transactional` pattern.

### Security model

- Form-based login, custom login page at `/Login`, `defaultSuccessUrl("/home", true)`.
- `CustomerUserDetailsService` loads `NguoiDung` from `NguoiDungRepository` and maps the `VaiTro` (role) field into Spring authorities.
- Roles in DB are stored as display strings; `ROLE_ADMIN` and `ROLE_STAFF` are the authority names (`hasRole("ADMIN")` checks for `ROLE_ADMIN`).
- Public endpoints whitelisted in `SecurityConfig` (search for `permitAll()`); everything else requires authentication.
- BCrypt password encoder bean.

### Key entities and their roles

- `Phong` (room) → `LoaiPhong` (room type), `TienNghiPhong` (amenities, many-to-many via composite key).
- `DatPhong` (booking) → `ChiTietDatPhong` (booking line items linking to `Phong`) and `Chi_tiet_dich_vu` (added services).
- `HoaDon` (invoice) ← one per booking, with `ThanhToan` (payment record).
- `NguoiDung` (user) / `Nhanvien` (staff) / `VaiTro` (role).
- `KhuyenMai` (promo) applied at booking time.
- `DanhGia` (review) per room, with admin reply.
- `VerificationToken` for email verification flow.

### Statistics

Two layered systems:
- Base totals: `ThongKeRepo` + `ThongKeService` + `AdminThongKeDoanhThu` controller.
- Advanced analytics: `AdvancedThongKeDTO` + `AdminThongKeNangCaoController` (the newer module — adds RevPAR, ADR, cancellation rate, returning-customer rate, payment-method breakdown). These are server-rendered into `templates/admin/thong-ke-doanh-thu.html` and `thong-ke.html`.

### PDF / Email

- `flying-saucer-pdf` and `itext` are on the classpath for invoice PDF rendering (see `templates/admin/hoa-don-pdf.html` and `templates/nhan-vien/hoa-don-pdf.html`).
- `MailSenderService` + `VerifyEmailService` + `VerifyEmailController` handle registration email confirmation.

## Conventions & Gotchas

- **Vietnamese in code, comments, and UI.** String literals, enum-like status fields (`trang_thai` on `Phong`: `Trong`, `Da dat`, ...), and `trang_thai` on `DatPhong` (`Chua thanh toan`, etc.) are Vietnamese — match the existing wording exactly when adding new states.
- **Mixed repo naming.** Both `*Repo` and `*Repository` suffixes are used; either is acceptable but stay consistent within a feature.
- **Lombok is the norm.** Don't add manual getters/setters on entities or DTOs.
- **Authorization is route-based**, not method-based, and lives in `SecurityConfig`. When you add a new URL, decide which gate it needs (admin / staff / public / authenticated) and update the matcher.
- **No automatic schema management.** `ddl-auto=none` — update the SQL DDL script separately when changing entities.
- **JPQL/Hibernate SQL is logged at DEBUG/TRACE** (`logging.level.org.hibernate.SQL=DEBUG`); useful when debugging repository queries.
- **Tests are minimal** — only the auto-generated `Su26sd09ApplicationTests` context-load test exists. When you add tests, follow Spring Boot starter-test conventions.
- **PDF templates live next to their HTML** in `templates/admin/` and `templates/nhan-vien/` and are rendered via Flying Saucer — keep CSS inline-friendly (avoid external `@import`s that the renderer can't resolve).


keep every files as it. you can add files for the new requirements. you can only modify the following files:
-ThongKeRepo.java,
-ThongKeService.java,
-thong-ke-doanh-thu.html,
-customer-reviews-page.html,
-ReviewService.java,
-DanhGiaRepo.java,
-CustomerReviewRequest.java,
-CustomerReviewViewDTO.java,
-RoomReviewReplyRequest.java,
-RoomReviewViewDTO.java,
-DanhGia.java,

requirements:
-keep UI modifications, additions consistent with the current style, theme,
modification/addition of files must be listed in the reply,
if you have idea or spot error you can request a list in the reply to be accepted,

