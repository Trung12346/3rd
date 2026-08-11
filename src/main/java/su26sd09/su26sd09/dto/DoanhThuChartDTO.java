package su26sd09.su26sd09.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;
@AllArgsConstructor
@Getter
public class DoanhThuChartDTO {
    List<String> labels;
    List<Double> revenues;
}
