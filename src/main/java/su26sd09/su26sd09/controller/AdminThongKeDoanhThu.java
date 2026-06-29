package su26sd09.su26sd09.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import su26sd09.su26sd09.dto.DoanhThuChartDTO;
import su26sd09.su26sd09.repository.ThongKeRepo;
import su26sd09.su26sd09.service.ThongKeService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;


@Controller
@RequestMapping("/admin/thong-ke")
public class AdminThongKeDoanhThu {
    @Autowired
    ThongKeRepo tkr;
    @Autowired
    ThongKeService tks;

    @GetMapping
    public String get_0(Model model,
                        @RequestParam(value = "tuNgay", required = false) LocalDate tuNgay,
                        @RequestParam(value = "denNgay", required = false) LocalDate denNgay,
                        @RequestParam(value = "xemTheo", required = false) String xemTheo)
    {


        if(tuNgay == null & denNgay == null)
        {
            int year = LocalDate.now().getYear();
            tuNgay = LocalDate.of(year, 1, 1);
            denNgay = LocalDate.of(year, 12, 31);

        }

        LocalDate bufferDate;
        if(tuNgay.isAfter(denNgay)) {
            bufferDate = tuNgay;
            tuNgay = denNgay;
            denNgay = bufferDate;
        }

        Long cycle = denNgay.toEpochDay() - tuNgay.toEpochDay();
        LocalDate pcStart = tuNgay.minusDays(cycle);
        LocalDate pcEnd = denNgay.minusDays(cycle);
        Double pcRevenue = tkr.getTotalRevenue(pcStart, pcEnd);
        Double revenue = tkr.getTotalRevenue(tuNgay, denNgay);
        Integer pcInvoice = tkr.getTotalInvoice(pcStart, pcEnd);
        Integer invoice = tkr.getTotalInvoice(tuNgay, denNgay);
        Double rGrowth = 0d;
        Integer iGrowth = 0;

        pcRevenue = pcRevenue == null ? 0d : pcRevenue;
        revenue = revenue == null ? 0d : revenue;
        pcInvoice = pcInvoice == null ? 0 : pcInvoice;
        invoice = invoice == null ? 0 : invoice;

        if(pcRevenue == 0 && revenue > 0)
        {
            rGrowth = null;
        } else if(pcRevenue == 0 && revenue == 0)
        {
            rGrowth = null;
        } else
        {
            rGrowth = (revenue - pcRevenue) / pcRevenue * 100d;
        }

        if(pcInvoice == 0 && invoice > 0)
        {
            iGrowth = null;
        } else if(pcInvoice == 0 && invoice == 0)
        {
            iGrowth = null;
        } else
        {
            iGrowth = invoice - pcInvoice;
        }

        model.addAttribute("tyLeTangTruong", rGrowth);
        model.addAttribute("soHoaDonChenhLech", iGrowth);
        model.addAttribute("tuNgay", tuNgay);
        model.addAttribute("denNgay", denNgay);
        model.addAttribute("xemTheo", xemTheo);

        model.addAttribute("tongDoanhThu", revenue);
        model.addAttribute("tongHoaDon", invoice);
        model.addAttribute("doanhThuTrungBinh", tkr.getAvgRevenue(tuNgay, denNgay));

        System.out.println("ASDJKOAHSDALSIJKDASDASLKDHJASDGAHSD");
        System.out.println(tkr.getOccupancy(tuNgay, denNgay));
        Double occupancyRate = tkr.getOccupancy(tuNgay, denNgay) / tkr.getTotalRoom(tuNgay, denNgay) * 100d;
        model.addAttribute("tyLeLapDay", occupancyRate);
        return "admin/thong-ke-doanh-thu";
    }

    @GetMapping("/doanh-thu")
    public ResponseEntity get_1(
            @RequestParam(value = "tuNgay") LocalDate tuNgay,
            @RequestParam(value = "denNgay") LocalDate denNgay,
            @RequestParam(value = "xemTheo") String xemTheo
    )
    {
        String pattern = switch(xemTheo) {
            case "ngay" -> "yyyy-MM-dd";
            case "nam" -> "yyyy";
            default -> "yyyy-MM";
        };
        List<Object[]> list = tks.refactorResult(tuNgay, denNgay, pattern);
        List<String> labels = new ArrayList<>();
        List<Double> revenues = new ArrayList<>();

        for(Object[] dto: list)
        {
            System.out.println("LAJKAAJKHASKHJDBAKJHSDHAHSDHAKSJHDJAHSGHD");
            System.out.println(dto);
            labels.add(dto[0].toString());
            revenues.add(Double.parseDouble(dto[1].toString()));
        }

        DoanhThuChartDTO dtcdto = new DoanhThuChartDTO(labels, revenues);

        return ResponseEntity.ok(dtcdto);
    }

    @GetMapping("/doanh-thu-theo-loai-phong")
    public ResponseEntity get_2(
            @RequestParam("tuNgay") LocalDate tuNgay,
            @RequestParam("denNgay") LocalDate denNgay
    )
    {
        List<Object[]> list = tkr.getRevenueByRoomType(tuNgay, denNgay);
        List<String> labels = new ArrayList<>();
        List<Double> revenues = new ArrayList<>();

        for(Object[] dto: list)
        {
            labels.add(dto[0].toString());
            revenues.add(Double.parseDouble(dto[1].toString()));
        }

        DoanhThuChartDTO dtcdto = new DoanhThuChartDTO(labels, revenues);

        return ResponseEntity.ok(dtcdto);
    }

    @GetMapping("/chi-tiet-doanh-thu-theo-loai-phong")
    public ResponseEntity get_3(
            @RequestParam("tuNgay") LocalDate tuNgay,
            @RequestParam("denNgay") LocalDate denNgay
    )
    {
        Long cycle = denNgay.toEpochDay() - tuNgay.toEpochDay();

        List<Object[]> list = tkr.getRevenueByRoomType(tuNgay, denNgay);
        List<Object[]> prevCycle = tkr.getRevenueByRoomType(tuNgay.minusDays(cycle), denNgay.minusDays(cycle));
        List<Object[]> body = new ArrayList<>();
        List<String> loais = tkr.getTenLoaiPhong();

        for(String loai: loais)
        {
            Double revenue = 0d;
            Double preRevenue = 0d;
            Double growthRate = 0d;
            for(Object[] l: list)
            {
                if(loai.equals(l[0].toString()))
                {
                    revenue = Double.parseDouble(l[1].toString());
                }
            }
            for(Object[] p: prevCycle)
            {
                if(loai.equals(p[0].toString()))
                {
                    preRevenue = Double.parseDouble(p[1].toString());
                }
            }
            if(preRevenue == 0d && revenue > 0d)
            {
                growthRate = null;
            } else if(preRevenue == 0d && revenue == 0d)
            {
                growthRate = null;
            } else {
                growthRate = (revenue - preRevenue) / preRevenue * 100d;
            }
            body.add(new Object[] {loai, revenue, growthRate});
        }

        return ResponseEntity.ok(body);
    }
}
