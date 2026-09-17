package com.linklife.circle;

import com.linklife.circle.dto.CircleVO;
import com.linklife.circle.dto.CreateCircleRequest;
import com.linklife.circle.dto.JoinCircleRequest;
import com.linklife.circle.dto.MemberVO;
import com.linklife.common.ratelimit.RateLimiter;
import com.linklife.common.security.UserContext;
import com.linklife.common.web.Result;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/circles")
@RequiredArgsConstructor
public class CircleController {

    private final CircleService circleService;
    private final RateLimiter rateLimiter;

    @PostMapping
    public Result<CircleVO> create(@Valid @RequestBody CreateCircleRequest request) {
        return Result.ok(circleService.create(UserContext.requireUserId(), request.name()));
    }

    @PostMapping("/join")
    public Result<CircleVO> join(@Valid @RequestBody JoinCircleRequest request) {
        rateLimiter.check(UserContext.requireUserId() + ":join");
        return Result.ok(circleService.join(UserContext.requireUserId(), request.inviteCode()));
    }

    @GetMapping
    public Result<List<CircleVO>> myCircles() {
        return Result.ok(circleService.listMyCircles(UserContext.requireUserId()));
    }

    @GetMapping("/{id}/members")
    public Result<List<MemberVO>> members(@PathVariable long id) {
        return Result.ok(circleService.listMembers(UserContext.requireUserId(), id));
    }
}
