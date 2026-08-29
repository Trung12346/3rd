package su26sd09.su26sd09.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import su26sd09.su26sd09.entity.GiayTo;

import java.util.List;

public interface GiayToRepo extends JpaRepository<GiayTo, Integer> {
    List<GiayTo> findByChiTietDatPhong_Id(int chiTietDatPhongId);

    // Goi y giay to cu theo so dinh danh (autocomplete o panel "Thong tin giay
    // to" cua So Do Phong): tim cac giay to da luu truoc day co so dinh danh
    // bat dau bang tu khoa nhap, moi nhat truoc, de dien lai toan bo thong tin
    // (ho ten, ngay sinh, que quan, noi cu tru...) cho khach quen quay lai.
    List<GiayTo> findTop8BySoDinhDanhStartingWithOrderByIdDesc(String soDinhDanhPrefix);

    @Query("""
        select g from GiayTo g
        left join g.chiTietDatPhong ct
        left join ct.p p
        left join ct.d d
        where (
            :keyword is null or :keyword = ''
            or lower(g.hoTen) like lower(concat('%', :keyword, '%'))
            or lower(g.soDinhDanh) like lower(concat('%', :keyword, '%'))
            or lower(p.soPhong) like lower(concat('%', :keyword, '%'))
        )
        and (:loaiGiayTo is null or :loaiGiayTo = '' or g.loaiGiayTo = :loaiGiayTo)
        and (:quocTich is null or :quocTich = '' or lower(g.quocTich) like lower(concat('%', :quocTich, '%')))
        and (:maDatPhong is null or d.id = :maDatPhong)
        order by g.id desc
    """)
    Page<GiayTo> searchFiltered(
            @Param("keyword") String keyword,
            @Param("loaiGiayTo") String loaiGiayTo,
            @Param("quocTich") String quocTich,
            @Param("maDatPhong") Integer maDatPhong,
            Pageable pageable
    );
}
