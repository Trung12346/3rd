package su26sd09.su26sd09.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
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
