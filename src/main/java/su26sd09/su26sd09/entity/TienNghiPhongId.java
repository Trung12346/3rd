package su26sd09.su26sd09.entity;

import java.io.Serializable;
import java.util.Objects;

public class TienNghiPhongId implements Serializable {
    private Integer phong;
    private Integer tienNghi;

    public TienNghiPhongId() {
    }

    public Integer getPhong() {
        return phong;
    }

    public void setPhong(Integer phong) {
        this.phong = phong;
    }

    public Integer getTienNghi() {
        return tienNghi;
    }

    public void setTienNghi(Integer tienNghi) {
        this.tienNghi = tienNghi;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TienNghiPhongId)) {
            return false;
        }
        TienNghiPhongId that = (TienNghiPhongId) o;
        return Objects.equals(phong, that.phong) && Objects.equals(tienNghi, that.tienNghi);
    }

    @Override
    public int hashCode() {
        return Objects.hash(phong, tienNghi);
    }
}
