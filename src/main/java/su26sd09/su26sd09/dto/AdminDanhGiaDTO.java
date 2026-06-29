package su26sd09.su26sd09.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import su26sd09.su26sd09.entity.DanhGia;
import su26sd09.su26sd09.entity.LoaiPhong;
import su26sd09.su26sd09.repository.DanhGiaRepo;
import su26sd09.su26sd09.repository.LoaiPhongRepository;

@Getter
@Setter
public class AdminDanhGiaDTO {
    public DanhGia danhGia;
    public LoaiPhong loaiPhong;

    @Autowired
    DanhGiaRepo dgRepo;

    public AdminDanhGiaDTO(DanhGia danhGia) {
        this.danhGia = danhGia;
        this.loaiPhong = dgRepo.findLoaiPhong(danhGia.id);
    }
}
