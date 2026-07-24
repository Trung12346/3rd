package su26sd09.su26sd09.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.util.UUID;

@Entity
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Table(name = "phong_anh")
@IdClass(PhongAnhId.class)
public class PhongAnh {
    @Id
    @ManyToOne
    @JoinColumn(name = "ma_anh")
    public Anh maAnh;

    @Id
    @ManyToOne
    @JoinColumn(name = "ma_phong")
    public Phong maPhong;
}
