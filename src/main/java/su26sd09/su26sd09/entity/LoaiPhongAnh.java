package su26sd09.su26sd09.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Table(name = "loai_phong_anh")
@IdClass(LoaiPhongAnhId.class)
public class LoaiPhongAnh {
    @Id
    @ManyToOne
    @JoinColumn(name = "ma_anh")
    public Anh maAnh;

    @Id
    @ManyToOne
    @JoinColumn(name = "ma_loai_phong")
    public LoaiPhong maLoaiPhong;
}
