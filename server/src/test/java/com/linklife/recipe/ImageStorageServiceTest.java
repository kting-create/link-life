package com.linklife.recipe;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.linklife.common.exception.BusinessException;
import com.linklife.common.exception.ErrorCode;
import com.linklife.recipe.service.ImageStorageService;
import java.io.UncheckedIOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ImageStorageServiceTest {

    static final byte[] JPG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0, 1, 2, 3, 4, 5, 6, 7, 8};

    @TempDir
    java.nio.file.Path tempDir;

    ImageStorageService storage() {
        return new ImageStorageService(tempDir.toString());
    }

    @Test
    void saveReadDeleteRoundTrip() {
        ImageStorageService storage = storage();
        String filePath = storage.save(1L, "step.jpg", JPG);
        assertEquals("images/recipes/1/", filePath.substring(0, "images/recipes/1/".length()));
        assertTrue(filePath.endsWith(".jpg"));
        assertArrayEquals(JPG, storage.read(filePath));

        storage.delete(filePath);
        assertThrows(UncheckedIOException.class, () -> storage.read(filePath));
        storage.delete(filePath);
    }

    @Test
    void saveAndValidateRejectBadExtension() {
        ImageStorageService storage = storage();
        BusinessException fromSave = assertThrows(BusinessException.class,
                () -> storage.save(1L, "evil.gif", JPG));
        assertEquals(ErrorCode.FILE_TYPE_INVALID, fromSave.getErrorCode());
        BusinessException fromValidate = assertThrows(BusinessException.class,
                () -> ImageStorageService.validate(JPG, "photo.exe"));
        assertEquals(ErrorCode.FILE_TYPE_INVALID, fromValidate.getErrorCode());
        BusinessException fromMagic = assertThrows(BusinessException.class,
                () -> ImageStorageService.validate("hello world jpg".getBytes(), "fake.jpg"));
        assertEquals(ErrorCode.FILE_TYPE_INVALID, fromMagic.getErrorCode());
        assertEquals("image/jpeg", ImageStorageService.contentType("images/recipes/1/a.jpg"));
    }

    @Test
    void rejectsPathTraversal() {
        ImageStorageService storage = storage();
        assertThrows(BusinessException.class, () -> storage.read("../../etc/passwd"));
        assertThrows(BusinessException.class, () -> storage.read("images/../../secret.jpg"));
        assertThrows(BusinessException.class, () -> storage.delete("../outside.jpg"));
    }
}
