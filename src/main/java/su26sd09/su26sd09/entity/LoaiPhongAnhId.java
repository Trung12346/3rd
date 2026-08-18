package su26sd09.su26sd09.entity;

import lombok.*;

import java.io.Serializable;
import java.util.UUID;
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@EqualsAndHashCode
public class LoaiPhongAnhId implements Serializable {

    public UUID maAnh;
    public Integer maLoaiPhong;
}
