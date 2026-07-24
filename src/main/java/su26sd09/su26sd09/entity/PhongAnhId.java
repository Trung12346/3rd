package su26sd09.su26sd09.entity;

import java.io.Serializable;
import java.util.UUID;
import lombok.*;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@EqualsAndHashCode
public class PhongAnhId implements Serializable {

    public UUID maAnh;
    public Integer maPhong;
}