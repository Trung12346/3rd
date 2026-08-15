package su26sd09.su26sd09.dto;

import java.time.LocalDate;

/**
 * Dai dien cho 1 khoang NGAY (chi ngay, khong gio) ma TAT CA phong dang hoat
 * dong cua 1 loai phong cu the deu da bi khoa lich (khong the chon lam ngay
 * nhan phong). Dung de FE (calendar) disable cac o ngay tuong ung, ma khong
 * anh huong toi cac DTO/khoang khoa lich theo TUNG PHONG da co san
 * (KhoangNgayBiKhoaDTO), vi 2 khai niem nay khac nhau (1 phong vs toan bo loai).
 *
 * tuNgay/denNgay la INCLUSIVE ca 2 dau (vi du tuNgay=10/08, denNgay=12/08
 * nghia la ngay 10, 11, 12 thang 8 deu khong con phong trong de nhan phong).
 */
public class NgayHetPhongDTO {

    private final LocalDate tuNgay;
    private final LocalDate denNgay;

    public NgayHetPhongDTO(LocalDate tuNgay, LocalDate denNgay) {
        this.tuNgay = tuNgay;
        this.denNgay = denNgay;
    }

    public LocalDate getTuNgay() {
        return tuNgay;
    }

    public LocalDate getDenNgay() {
        return denNgay;
    }
}
