package su26sd09.su26sd09.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import su26sd09.su26sd09.dto.AdvancedThongKeDTO;
import su26sd09.su26sd09.repository.ThongKeRepo;

import java.time.LocalDate;
import java.time.Year;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class ThongKeService {
    @Autowired
    ThongKeRepo tkr;

    private static double toDouble(Object o) {
        return o == null ? 0d : Double.parseDouble(o.toString());
    }

    private static int toInt(Object o) {
        return o == null ? 0 : Integer.parseInt(o.toString());
    }

    private static double safeDivPercent(double numerator, double denominator) {
        if (denominator == 0) return 0d;
        return numerator / denominator * 100d;
    }

    /**
     * Tong hop cac chi so phan tich chuyen sau: RevPAR, ADR, ty le huy, ty le khach quay lai,
     * co cau doanh thu, phuong thuc thanh toan va trang thai don dat phong.
     */
    public AdvancedThongKeDTO getAdvancedStats(LocalDate tuNgay, LocalDate denNgay) {
        List<Object[]> breakdownRows = tkr.getRevenueBreakdown(tuNgay, denNgay);
        Object[] breakdown = breakdownRows.isEmpty() ? new Object[]{0, 0, 0, 0} : breakdownRows.get(0);
        double doanhThuPhong = toDouble(breakdown[0]);
        double doanhThuDichVu = toDouble(breakdown[1]);
        double tienGiam = toDouble(breakdown[2]);
        double tienVat = toDouble(breakdown[3]);

        Integer roomNightsSold = tkr.getOccupancy(tuNgay, denNgay);
        Double availableRoomNights = tkr.getTotalRoom(tuNgay, denNgay);
        double nightsSold = roomNightsSold == null ? 0 : roomNightsSold;
        double nightsAvailable = availableRoomNights == null ? 0 : availableRoomNights;
        double adr = nightsSold == 0 ? 0d : doanhThuPhong / nightsSold;
        double revPar = nightsAvailable == 0 ? 0d : doanhThuPhong / nightsAvailable;

        List<Object[]> cancelRows = tkr.getCancellationStats(tuNgay, denNgay);
        Object[] cancel = cancelRows.isEmpty() ? new Object[]{0, 0} : cancelRows.get(0);
        double tyLeHuy = safeDivPercent(toDouble(cancel[1]), toDouble(cancel[0]));

        List<Object[]> repeatRows = tkr.getRepeatGuestStats(tuNgay, denNgay);
        Object[] repeat = repeatRows.isEmpty() ? new Object[]{0, 0} : repeatRows.get(0);
        double tyLeKhachQuayLai = safeDivPercent(toDouble(repeat[1]), toDouble(repeat[0]));

        List<Object[]> paymentRows = tkr.getPaymentMethodBreakdown(tuNgay, denNgay);
        List<String> paymentLabels = new ArrayList<>();
        List<Double> paymentValues = new ArrayList<>();
        for (Object[] row : paymentRows) {
            paymentLabels.add(row[0] == null ? "Khac" : row[0].toString());
            paymentValues.add(toDouble(row[1]));
        }

        List<Object[]> statusRows = tkr.getBookingStatusBreakdown(tuNgay, denNgay);
        List<String> statusLabels = new ArrayList<>();
        List<Integer> statusValues = new ArrayList<>();
        for (Object[] row : statusRows) {
            statusLabels.add(row[0] == null ? "Khac" : row[0].toString());
            statusValues.add(toInt(row[1]));
        }

        return new AdvancedThongKeDTO(
                revPar, adr, tyLeHuy, tyLeKhachQuayLai,
                doanhThuPhong, doanhThuDichVu, tienGiam, tienVat,
                paymentLabels, paymentValues,
                statusLabels, statusValues
        );
    }

    /**
     * Top 10 phong theo doanh thu, kem ty trong % tren tong doanh thu phong (theo chi tiet dat phong) trong ky.
     * Moi hang: [so_phong, ten_loai, luot_dat, doanh_thu, ty_trong_pct]
     */
    public List<Object[]> getTopPhong(LocalDate tuNgay, LocalDate denNgay) {
        List<Object[]> rows = tkr.getTopPhongDoanhThu(tuNgay, denNgay);
        Double tongRaw = tkr.getTongDoanhThuPhongChiTiet(tuNgay, denNgay);
        double tong = (tongRaw == null || tongRaw == 0) ? 0 : tongRaw;
        List<Object[]> result = new ArrayList<>();
        for (Object[] r : rows) {
            double doanhThu = toDouble(r[3]);
            double tyTrong = tong == 0 ? 0 : doanhThu / tong * 100d;
            result.add(new Object[]{r[0], r[1], r[2], doanhThu, tyTrong});
        }
        return result;
    }

    /**
     * Top 10 dich vu theo doanh thu, kem ty trong % tren tong doanh thu dich vu (theo chi tiet dich vu) trong ky.
     * Moi hang: [ten_dich_vu, so_luong, doanh_thu, ty_trong_pct]
     */
    public List<Object[]> getTopDichVu(LocalDate tuNgay, LocalDate denNgay) {
        List<Object[]> rows = tkr.getTopDichVuDoanhThu(tuNgay, denNgay);
        Double tongRaw = tkr.getTongDoanhThuDichVuChiTiet(tuNgay, denNgay);
        double tong = (tongRaw == null || tongRaw == 0) ? 0 : tongRaw;
        List<Object[]> result = new ArrayList<>();
        for (Object[] r : rows) {
            double doanhThu = toDouble(r[2]);
            double tyTrong = tong == 0 ? 0 : doanhThu / tong * 100d;
            result.add(new Object[]{r[0], r[1], doanhThu, tyTrong});
        }
        return result;
    }

    /**
     * Top 10 khach hang chi tieu nhieu nhat trong ky, kem ty trong % tren tong doanh thu (hoa don) trong ky.
     * Moi hang: [ho_ten, email, so_lan_dat, tong_chi_tieu, ty_trong_pct]
     */
    public List<Object[]> getTopKhachHang(LocalDate tuNgay, LocalDate denNgay) {
        List<Object[]> rows = tkr.getTopKhachHang(tuNgay, denNgay);
        Double tongRaw = tkr.getTotalRevenue(tuNgay, denNgay);
        double tong = (tongRaw == null || tongRaw == 0) ? 0 : tongRaw;
        List<Object[]> result = new ArrayList<>();
        for (Object[] r : rows) {
            double chiTieu = toDouble(r[3]);
            double tyTrong = tong == 0 ? 0 : chiTieu / tong * 100d;
            result.add(new Object[]{r[0], r[1], r[2], chiTieu, tyTrong});
        }
        return result;
    }

//    public Double proc(List<Object[]> data, Integer dInd, String label)
//    {
//        if(data.get(dInd)[0].toString().equals(label)) {
//            dInd.
//        }
//    }

    public List<Object[]> refactorResult(LocalDate tuNgay, LocalDate denNgay, String pattern)
    {
        List<Object[]> list = new ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
        List<Object[]> data = tkr.sumDoanhThuTheoThoiGian(tuNgay, denNgay, pattern);
        int dataIndex = 0;

        switch(pattern)
        {
            case "yyyy-MM-dd" ->
                    {
                        for(LocalDate d = tuNgay; !d.isAfter(denNgay); d = d.plusDays(1))
                        {
                            String label = d.format(formatter);
                            Double revenue = 0.0;
                            if(dataIndex < data.size() && data.get(dataIndex)[0].toString().equals(label)) {
                                revenue = Double.parseDouble(data.get(dataIndex)[1].toString());
                                dataIndex++;
                            }
                            list.add(new Object[]
                                    {
                                            label,
                                            revenue
                                    });
                        }
                        return list;
                    }
            case "yyyy" ->
                    {
                        Year denNam = Year.from(denNgay);
                        for(Year d = Year.from(tuNgay); !d.isAfter(denNam); d = d.plusYears(1))
                        {
                            String label = d.format(formatter);
                            Double revenue = 0.0;
                            if(dataIndex < data.size() && data.get(dataIndex)[0].toString().equals(label)) {
                                revenue = Double.parseDouble(data.get(dataIndex)[1].toString());
                                dataIndex++;
                            }
                            list.add(new Object[]
                                    {
                                            label,
                                            revenue
                                    });
                        }
                        return list;
                    }
            default ->
                    {
                        YearMonth denThang = YearMonth.from(denNgay);
                        for(YearMonth d = YearMonth.from(tuNgay); !d.isAfter(denThang); d = d.plusMonths(1))
                        {
                            String label = d.format(formatter);
                            Double revenue = 0.0;
                            if(dataIndex < data.size() && data.get(dataIndex)[0].toString().equals(label)) {
                                revenue = Double.parseDouble(data.get(dataIndex)[1].toString());
                                dataIndex++;
                            }
                            list.add(new Object[]
                                    {
                                            label,
                                            revenue
                                    });
                        }
                        return list;
                    }

        }
    }
}
