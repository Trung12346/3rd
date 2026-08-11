package su26sd09.su26sd09.entity;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Table(name = "anh")
public class Anh {
    @Id
    @Column(name = "ma_anh")
    public UUID maAnh;

    @Column(name = "src")
    public String src;

    @Column(name = "kieu_file")
    public String kieuFile;
}
