package com.linklife.recipe.service;

import com.linklife.common.exception.BusinessException;
import com.linklife.common.exception.ErrorCode;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ImageStorageService {

    public static final long MAX_FILE_BYTES = 5 * 1024 * 1024;
    private static final Set<String> ALLOWED_EXT = Set.of("jpg", "jpeg", "png", "webp");
    private static final Map<String, String> CONTENT_TYPES = Map.of(
            "jpg", "image/jpeg", "jpeg", "image/jpeg", "png", "image/png", "webp", "image/webp");

    private final Path baseDir;

    public ImageStorageService(@Value("${link.images.base-dir:/data/images}") String baseDir) {
        this.baseDir = Paths.get(baseDir);
    }

    public String save(long recipeId, String originalFilename, byte[] bytes) {
        String ext = extension(originalFilename);
        if (ext == null || !ALLOWED_EXT.contains(ext)) {
            throw new BusinessException(ErrorCode.FILE_TYPE_INVALID);
        }
        try {
            Path dir = baseDir.resolve("images").resolve("recipes").resolve(String.valueOf(recipeId));
            Files.createDirectories(dir);
            String name = UUID.randomUUID() + "." + ext;
            Files.write(dir.resolve(name), bytes);
            return "images/recipes/" + recipeId + "/" + name;
        } catch (IOException e) {
            throw new UncheckedIOException("save image failed", e);
        }
    }

    public byte[] read(String filePath) {
        try {
            return Files.readAllBytes(baseDir.resolve(filePath));
        } catch (IOException e) {
            throw new UncheckedIOException("read image failed", e);
        }
    }

    public void delete(String filePath) {
        try {
            Files.deleteIfExists(baseDir.resolve(filePath));
        } catch (IOException e) {
            throw new UncheckedIOException("delete image failed", e);
        }
    }

    public static void validate(byte[] bytes, String originalFilename) {
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_FILE_BYTES) {
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE);
        }
        String ext = extension(originalFilename);
        if (ext == null || !ALLOWED_EXT.contains(ext)) {
            throw new BusinessException(ErrorCode.FILE_TYPE_INVALID);
        }
        if (!magicOk(bytes, ext)) {
            throw new BusinessException(ErrorCode.FILE_TYPE_INVALID);
        }
    }

    public static String contentType(String filePath) {
        String ext = extension(filePath);
        return ext == null ? "application/octet-stream" : CONTENT_TYPES.getOrDefault(ext, "application/octet-stream");
    }

    private static boolean magicOk(byte[] b, String ext) {
        if (b.length < 12) {
            return false;
        }
        if ("jpg".equals(ext) || "jpeg".equals(ext)) {
            return (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF;
        }
        if ("png".equals(ext)) {
            return (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G';
        }
        if ("webp".equals(ext)) {
            return b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                    && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P';
        }
        return false;
    }

    private static String extension(String filename) {
        if (filename == null) {
            return null;
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return null;
        }
        return filename.substring(dot + 1).toLowerCase();
    }
}
