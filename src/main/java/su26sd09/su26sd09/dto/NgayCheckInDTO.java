package su26sd09.su26sd09.dto;

import java.time.LocalDate;

public class NgayCheckInDTO {

    private LocalDate ngay;
    private long soDon;
    private boolean laHomNay;
    private boolean dangChon;

    public NgayCheckInDTO() {
    }

    public NgayCheckInDTO(LocalDate ngay, long soDon, boolean laHomNay, boolean dangChon) {
        this.ngay = ngay;
        this.soDon = soDon;
        this.laHomNay = laHomNay;
        this.dangChon = dangChon;
    }

    public LocalDate getNgay() {
        return ngay;
    }

    public void setNgay(LocalDate ngay) {
        this.ngay = ngay;
    }

    public long getSoDon() {
        return soDon;
    }

    public void setSoDon(long soDon) {
        this.soDon = soDon;
    }

    public boolean isLaHomNay() {
        return laHomNay;
    }

    public void setLaHomNay(boolean laHomNay) {
        this.laHomNay = laHomNay;
    }

    public boolean isDangChon() {
        return dangChon;
    }

    public void setDangChon(boolean dangChon) {
        this.dangChon = dangChon;
    }
}