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

    public List<LoaiPhong> findAll() {
        return repo.findAll();
    }

    public LoaiPhong findbyid(int id) {
        return repo.findById(id).orElse(null);
    }

    public void delete(LoaiPhong p) {
        loaiPhongAnhRepository.deleteByMaLoaiPhong_Id(p.getId());
        repo.delete(p);
    }

    public void save(LoaiPhong c) {
        repo.save(c);
    }

    @Transactional
    public void save(LoaiPhong c, List<UUID> anhIds) {
        LoaiPhong saved = repo.save(c);
        saveAnhLoaiPhong(saved, anhIds);
        if (saved.getGiaCoBan() != null) {
            repoPhong.capNhatGiaTheoLoaiPhong(saved.getId(), saved.getGiaCoBan());
        }
    }

    /**
     * Danh sách anh (hinh anh) cua 1 loai phong
     */
    public List<Anh> findAnhByLoaiPhong(int maLoaiPhong) {
        return loaiPhongAnhRepository.findByMaLoaiPhong_Id(maLoaiPhong)
                .stream()
                .map(LoaiPhongAnh::getMaAnh)
                .toList();
    }

    /**
     * Dong bo danh sach anh cua loai phong
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

    public List<LoaiPhong> findbyName(String name) {
        return repo.findbyName(name);
    }

    public boolean CheckTrungLoai(LoaiPhong l) {
        for (LoaiPhong p : findAll()) {
            if (p.tenLoai.equals(l.tenLoai) && p.id != l.id) {
                return true;
            }
        }
        return false;
    }

    public List timKiem(String keyword) {
        return repo.findbyName(keyword);
    }

    /**
     * Tìm kiếm loại phòng có phân trang - SỬ DỤNG JPA QUERY
     */
    public Page<LoaiPhong> searchPaged(String keyword, BigDecimal minGia, BigDecimal maxGia,
                                       Integer soKhach, Pageable pageable) {
        System.out.println("========== SEARCH PAGED ==========");
        System.out.println("keyword: " + keyword);
        System.out.println("minGia: " + minGia);
        System.out.println("maxGia: " + maxGia);
        System.out.println("soKhach: " + soKhach);

        String kw = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
        Page<LoaiPhong> result = repo.searchLoaiPhongPaged(kw, minGia, maxGia, soKhach, pageable);

        System.out.println("Total elements: " + result.getTotalElements());
        System.out.println("Content size: " + result.getContent().size());
        result.getContent().forEach(lp -> System.out.println("  - " + lp.getTenLoai()));

        return result;
    }

    /**
     * Tìm kiếm loại phòng có phân trang - SỬ DỤNG NATIVE QUERY
     * Hỗ trợ tìm kiếm không phân biệt dấu (accent-insensitive)
     */
    public Page<LoaiPhong> searchLoaiPhongPagedNative(String keyword, BigDecimal minGia, BigDecimal maxGia,
                                                      Integer soKhach, Pageable pageable) {
        String kw = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
        int offset = (int) pageable.getOffset();
        int limit = pageable.getPageSize();

        List<LoaiPhong> content = repo.searchLoaiPhongPagedNative(kw, minGia, maxGia, soKhach, offset, limit);
        long total = repo.countSearchLoaiPhongPagedNative(kw, minGia, maxGia, soKhach);

        return new org.springframework.data.domain.PageImpl<>(content, pageable, total);
    }

    public String checkReference(int id) {
        Phong p = repoPhong.findFirstByloaiPhongId(id) == null ? null : repoPhong.findFirstByloaiPhongId(id);
        if (p == null) {
            return null;
        }
        return p.getSoPhong();
    }
}