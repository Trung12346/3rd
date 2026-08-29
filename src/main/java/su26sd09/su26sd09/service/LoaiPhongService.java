package su26sd09.su26sd09.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import su26sd09.su26sd09.entity.Anh;
import su26sd09.su26sd09.entity.LoaiPhong;
import su26sd09.su26sd09.entity.LoaiPhongAnh;
import su26sd09.su26sd09.entity.Phong;
import su26sd09.su26sd09.repository.AnhRepository;
import su26sd09.su26sd09.repository.LoaiPhongAnhRepository;
import su26sd09.su26sd09.repository.LoaiPhongRepository;
import su26sd09.su26sd09.repository.PhongRepository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class LoaiPhongService {

    @Autowired
    LoaiPhongRepository repo;
    @Autowired
    PhongRepository repoPhong;
    @Autowired
    LoaiPhongAnhRepository loaiPhongAnhRepository;
    @Autowired
    AnhRepository anhRepository;


    public List<LoaiPhong> findAll(){
        return repo.findAll();
    }

    public LoaiPhong findbyid(int id){
        return repo.findById(id).orElse(null);
    }

    public void delete(LoaiPhong p){
        loaiPhongAnhRepository.deleteByMaLoaiPhong_Id(p.getId());
        repo.delete(p);
    }

    public void save(LoaiPhong c){
        repo.save(c);
    }

    @Transactional
    public void save(LoaiPhong c, List<UUID> anhIds) {
        LoaiPhong saved = repo.save(c);
        saveAnhLoaiPhong(saved, anhIds);
        // Dong bo lai gia/dem cua tat ca phong thuoc loai phong nay theo gia
        // co ban MOI vua luu - truoc day chi Phong duoc tao/luu lai sau do moi
        // tu dong lay dung gia (xem "Gia tu dong lay theo loai phong" o form
        // Phong), khien cac phong cu bi "ket" gia cu sau khi doi gia loai phong.
        if (saved.getGiaCoBan() != null) {
            repoPhong.capNhatGiaTheoLoaiPhong(saved.getId(), saved.getGiaCoBan());
        }
    }

    /**
     * Danh sach anh (hinh anh) cua 1 loai phong, dung cho gallery
     * upload/edit/delete nhieu anh giong Phong.
     */
    public List<Anh> findAnhByLoaiPhong(int maLoaiPhong) {
        return loaiPhongAnhRepository.findByMaLoaiPhong_Id(maLoaiPhong)
                .stream()
                .map(LoaiPhongAnh::getMaAnh)
                .toList();
    }

    /**
     * Dong bo danh sach anh cua loai phong: xoa het lien ket cu trong
     * loai_phong_anh roi tao lai theo danh sach maAnh gui len tu form
     * (giu anh cu duoc tick chon + them anh moi vua upload qua AnhController,
     * anh nao bi bo chon/xoa se mat lien ket). Ban ghi Anh goc khong bi xoa.
     */
    private void saveAnhLoaiPhong(LoaiPhong loaiPhong, List<UUID> anhIds) {
        loaiPhongAnhRepository.deleteByMaLoaiPhong_Id(loaiPhong.getId());

        if (anhIds == null || anhIds.isEmpty()) {
            return;
        }

        List<Anh> anhs = anhRepository.findAllById(anhIds);

        List<LoaiPhongAnh> loaiPhongAnhs = new ArrayList<>();
        for (Anh anh : anhs) {
            LoaiPhongAnh loaiPhongAnh = new LoaiPhongAnh();
            loaiPhongAnh.setMaLoaiPhong(loaiPhong);
            loaiPhongAnh.setMaAnh(anh);
            loaiPhongAnhs.add(loaiPhongAnh);
        }

        loaiPhongAnhRepository.saveAll(loaiPhongAnhs);
    }

    public List<LoaiPhong> findbyName(String name){
        return repo.findbyName(name);
    }

    public boolean CheckTrungLoai(LoaiPhong l){
        for (LoaiPhong p : findAll()){
            if(p.tenLoai.equals(l.tenLoai) && p.id != l.id ){
                return true;
            }
        }
        return false;
    }

    public List timKiem(String keyword){
        return repo.findbyName(keyword);
    }


    public Page<LoaiPhong> searchPaged(String keyword, BigDecimal minGia, BigDecimal maxGia,
                                       Integer soKhach, Pageable pageable) {
        String kw = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
        return repo.searchLoaiPhongPaged(kw, minGia, maxGia, soKhach, pageable);
    }

    public String  checkReference(int id){
        Phong p = repoPhong.findFirstByloaiPhongId(id) == null ? null : repoPhong.findFirstByloaiPhongId(id);
        if (p == null){
            return null;
        }
        return p.getSoPhong();

    }
}
