package su26sd09.su26sd09.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;

@Data
@Entity
@Table(name = "tien_nghi_phong")
@IdClass(TienNghiPhongId.class)
public class TienNghiPhong {

    @Id
    @ManyToOne
    @JoinColumn(name = "ma_phong", referencedColumnName = "ma_phong")
    private Phong phong;

    @Id
    @ManyToOne
    @JoinColumn(name = "ma_tien_nghi", referencedColumnName = "ma_tien_nghi")
    private TienNghi tienNghi;
}
