package com.linklife.circle;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.linklife.circle.dto.CircleVO;
import com.linklife.circle.dto.MemberVO;
import com.linklife.circle.entity.Circle;
import com.linklife.circle.entity.CircleMember;
import com.linklife.circle.mapper.CircleMapper;
import com.linklife.circle.mapper.CircleMemberMapper;
import com.linklife.common.exception.BusinessException;
import com.linklife.common.exception.ErrorCode;
import com.linklife.user.entity.User;
import com.linklife.user.mapper.UserMapper;
import java.security.SecureRandom;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CircleService {

    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final CircleMapper circleMapper;
    private final CircleMemberMapper circleMemberMapper;
    private final UserMapper userMapper;

    @Transactional
    public CircleVO create(long userId, String name) {
        Circle circle = new Circle();
        circle.setName(name);
        circle.setOwnerId(userId);
        circle.setInviteCode(generateCode());
        circleMapper.insert(circle);

        CircleMember member = new CircleMember();
        member.setCircleId(circle.getId());
        member.setUserId(userId);
        member.setRole("OWNER");
        circleMemberMapper.insert(member);
        return toVO(circle);
    }

    @Transactional
    public CircleVO join(long userId, String inviteCode) {
        Circle circle = circleMapper.selectOne(
                new LambdaQueryWrapper<Circle>().eq(Circle::getInviteCode, inviteCode));
        if (circle == null) {
            throw new BusinessException(ErrorCode.INVITE_CODE_INVALID);
        }
        Long count = circleMemberMapper.selectCount(new LambdaQueryWrapper<CircleMember>()
                .eq(CircleMember::getCircleId, circle.getId())
                .eq(CircleMember::getUserId, userId));
        if (count > 0) {
            throw new BusinessException(ErrorCode.ALREADY_MEMBER);
        }
        CircleMember member = new CircleMember();
        member.setCircleId(circle.getId());
        member.setUserId(userId);
        member.setRole("MEMBER");
        circleMemberMapper.insert(member);
        return toVO(circle);
    }

    public List<CircleVO> listMyCircles(long userId) {
        List<Long> circleIds = circleMemberMapper.selectList(
                        new LambdaQueryWrapper<CircleMember>().eq(CircleMember::getUserId, userId))
                .stream().map(CircleMember::getCircleId).toList();
        if (circleIds.isEmpty()) {
            return List.of();
        }
        return circleMapper.selectBatchIds(circleIds).stream().map(this::toVO).toList();
    }

    public List<MemberVO> listMembers(long userId, long circleId) {
        requireMembership(userId, circleId);
        return circleMemberMapper.selectList(
                        new LambdaQueryWrapper<CircleMember>().eq(CircleMember::getCircleId, circleId))
                .stream().map(m -> {
                    User u = userMapper.selectById(m.getUserId());
                    return new MemberVO(m.getUserId(), u.getNickname(), u.getAvatar(), m.getRole());
                }).toList();
    }

    public void requireMembership(long userId, long circleId) {
        if (circleMapper.selectById(circleId) == null) {
            throw new BusinessException(ErrorCode.CIRCLE_NOT_FOUND);
        }
        Long count = circleMemberMapper.selectCount(new LambdaQueryWrapper<CircleMember>()
                .eq(CircleMember::getCircleId, circleId)
                .eq(CircleMember::getUserId, userId));
        if (count == 0) {
            throw new BusinessException(ErrorCode.NOT_CIRCLE_MEMBER);
        }
    }

    private String generateCode() {
        StringBuilder sb = new StringBuilder(8);
        for (int i = 0; i < 8; i++) {
            sb.append(CODE_CHARS.charAt(RANDOM.nextInt(CODE_CHARS.length())));
        }
        return sb.toString();
    }

    private CircleVO toVO(Circle circle) {
        return new CircleVO(circle.getId(), circle.getName(), circle.getInviteCode(), circle.getOwnerId());
    }
}
