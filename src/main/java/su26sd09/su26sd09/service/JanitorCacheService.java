package su26sd09.su26sd09.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import su26sd09.su26sd09.dto.PhongVeSinhAssignment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cache trong bộ nhớ (đồng thời được đồng bộ ra file JSON ở project root) cho
 * các phân công dọn phòng đang hoạt động: maPhong -> PhongVeSinhAssignment.
 * <p>
 * File cache giúp trạng thái phân công không bị mất khi ứng dụng khởi động
 * lại (engine phân công chạy nền sẽ đọc lại cache này thay vì phân công lại
 * từ đầu). Toàn bộ thao tác đọc/ghi map đều được đồng bộ hoá (synchronized)
 * vì cache được truy cập đồng thời bởi: request thread (khi nhân viên vệ sinh
 * upload ảnh / lễ tân xác nhận) và thread lập lịch của {@code JanitorAssignmentEngine}.
 */
@Service
public class JanitorCacheService {

    private static final Path CACHE_FILE = Paths.get("janitor-cache.json");

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final Map<Integer, PhongVeSinhAssignment> cache = new ConcurrentHashMap<>();

    @PostConstruct
    public synchronized void load() {
        try {
            if (Files.exists(CACHE_FILE)) {
                Map<String, PhongVeSinhAssignment> raw = objectMapper.readValue(
                        Files.readAllBytes(CACHE_FILE),
                        objectMapper.getTypeFactory().constructMapType(LinkedHashMap.class, String.class, PhongVeSinhAssignment.class));
                cache.clear();
                for (PhongVeSinhAssignment a : raw.values()) {
                    if (a != null) cache.put(a.getMaPhong(), a);
                }
            }
        } catch (IOException e) {
            System.err.println("Khong the doc janitor-cache.json, bat dau voi cache rong: " + e.getMessage());
        }
    }

    private synchronized void persist() {
        try {
            Map<String, PhongVeSinhAssignment> toWrite = new LinkedHashMap<>();
            for (Map.Entry<Integer, PhongVeSinhAssignment> e : cache.entrySet()) {
                toWrite.put(String.valueOf(e.getKey()), e.getValue());
            }
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(CACHE_FILE.toFile(), toWrite);
        } catch (IOException e) {
            System.err.println("Khong the ghi janitor-cache.json: " + e.getMessage());
        }
    }

    public Collection<PhongVeSinhAssignment> getAll() {
        return cache.values();
    }

    public Optional<PhongVeSinhAssignment> get(int maPhong) {
        return Optional.ofNullable(cache.get(maPhong));
    }

    public Optional<PhongVeSinhAssignment> findByNhanVien(int maNhanVien) {
        return cache.values().stream().filter(a -> a.getMaNhanVien() == maNhanVien).findFirst();
    }

    public boolean isAssigned(int maPhong) {
        return cache.containsKey(maPhong);
    }

    public void upsert(PhongVeSinhAssignment assignment) {
        cache.put(assignment.getMaPhong(), assignment);
        persist();
    }

    public void remove(int maPhong) {
        if (cache.remove(maPhong) != null) {
            persist();
        }
    }
}
