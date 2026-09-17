package com.linklife.order;

import com.linklife.common.security.UserContext;
import com.linklife.common.web.Result;
import com.linklife.order.dto.AddItemRequest;
import com.linklife.order.dto.CreateSheetRequest;
import com.linklife.order.dto.SheetDetailVO;
import com.linklife.order.dto.ItemVO;
import com.linklife.order.dto.UpdateItemStatusRequest;
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

    @PostMapping("/items")
    public Result<ItemVO> addItem(@Valid @RequestBody AddItemRequest request) {
        return Result.ok(orderService.addItem(
                UserContext.requireUserId(), request.sheetId(), request.dishName(), request.note()));
    }

    @PostMapping("/items/{id}/claim")
    public Result<ItemVO> claim(@PathVariable long id) {
        return Result.ok(orderService.claim(UserContext.requireUserId(), id));
    }

    @PostMapping("/items/{id}/release")
    public Result<ItemVO> release(@PathVariable long id) {
        return Result.ok(orderService.release(UserContext.requireUserId(), id));
    }

    @PostMapping("/items/{id}/status")
    public Result<ItemVO> updateItemStatus(@PathVariable long id,
            @Valid @RequestBody UpdateItemStatusRequest request) {
        return Result.ok(orderService.updateItemStatus(
                UserContext.requireUserId(), id, request.itemStatus()));
    }
}
