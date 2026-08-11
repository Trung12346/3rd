package su26sd09.su26sd09.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.*;

import java.util.List;

@Getter
@AllArgsConstructor
public class NhomYeuCauPhongDTO {
    private final String tenLoaiPhong;
    private final List<SlotPhongDTO> slots;
}
