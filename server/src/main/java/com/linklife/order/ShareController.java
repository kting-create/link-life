package com.linklife.order;

import com.linklife.common.web.Result;
import com.linklife.order.dto.SheetDetailVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ShareController {

    private final ShareService shareService;

    @GetMapping("/api/share/{token}")
    public Result<SheetDetailVO> getByToken(@PathVariable String token) {
        return Result.ok(shareService.getByToken(token));
    }
}
