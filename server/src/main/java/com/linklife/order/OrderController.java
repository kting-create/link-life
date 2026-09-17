package com.linklife.order;

import com.linklife.common.security.UserContext;
import com.linklife.common.web.Result;
import com.linklife.order.dto.CreateSheetRequest;
import com.linklife.order.dto.SheetDetailVO;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping("/sheets")
    public Result<SheetDetailVO> createSheet(@Valid @RequestBody CreateSheetRequest request) {
        return Result.ok(orderService.createSheet(
                UserContext.requireUserId(), request.circleId(), request.title(), request.items()));
    }

    @GetMapping("/sheets")
    public Result<List<SheetDetailVO>> listSheets(@RequestParam long circleId) {
        return Result.ok(orderService.listSheets(UserContext.requireUserId(), circleId));
    }

    @GetMapping("/sheets/{id}")
    public Result<SheetDetailVO> getSheet(@PathVariable long id) {
        return Result.ok(orderService.getSheet(UserContext.requireUserId(), id));
    }
}
