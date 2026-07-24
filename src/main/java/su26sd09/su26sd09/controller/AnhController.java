package su26sd09.su26sd09.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import su26sd09.su26sd09.entity.Anh;
import su26sd09.su26sd09.repository.AnhRepository;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

@Controller
@RequestMapping("/media")
public class AnhController {

    @Autowired
    AnhRepository anhRepository;

    private static final Path IMAGE_DIR = Paths.get("media");

    @ResponseBody
    @PostMapping
    public ResponseEntity<UUID> upload(
            @RequestParam("file") MultipartFile file) throws Exception {

        if (file.isEmpty())
            return ResponseEntity.badRequest().build();

        Files.createDirectories(IMAGE_DIR);

        byte[] bytes = file.getBytes();

        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(bytes);
        digest.update(Long.toString(System.currentTimeMillis()).getBytes(StandardCharsets.UTF_8));

        String hash = HexFormat.of().formatHex(digest.digest());

        String extension = "";

        String original = file.getOriginalFilename();
        if (original != null && original.contains(".")) {
            extension = original.substring(original.lastIndexOf('.'));
        }

        String fileName = hash + extension;

        Path destination = IMAGE_DIR.resolve(fileName);

        Files.write(destination, bytes);

        Anh anh = new Anh();
        anh.setMaAnh(UUID.randomUUID());
        anh.setKieuFile(extension);
        anh.setSrc(fileName);

        anhRepository.save(anh);

        return ResponseEntity.ok(anh.getMaAnh());
    }

    @ResponseBody
    @GetMapping("/{id}")
    public ResponseEntity<byte[]> getImage(@PathVariable UUID id) throws IOException {
        System.out.println("ASLDHKLASHDUHGLASKDHLOIAHLDAUHSDAHSDAHSDHLKAHSDJHALSJDHAJSHDJKAHLDJHALSJKDHLAJHDLJAHLSDSJHAS===============================================");
        Anh anh = anhRepository.findById(id).orElseThrow();

        Path path = IMAGE_DIR.resolve(anh.getSrc());

        String mimeType = switch (anh.getKieuFile().toLowerCase()) {
            case ".jpg", ".jpeg" -> MediaType.IMAGE_JPEG_VALUE;
            case ".png" -> MediaType.IMAGE_PNG_VALUE;
            case ".gif" -> MediaType.IMAGE_GIF_VALUE;
            case ".webp" -> "image/webp";
            case ".avif" -> "image/avif";
            default -> MediaType.APPLICATION_OCTET_STREAM_VALUE;
        };

        System.out.println(mimeType);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(mimeType))
                .body(Files.readAllBytes(path));
    }
    @GetMapping("/test")
    public String get()
    {
        return "test-anh";
    }
}
