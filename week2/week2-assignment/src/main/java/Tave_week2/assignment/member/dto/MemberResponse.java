package Tave_week2.assignment.member.dto;

import Tave_week2.assignment.member.domain.Member;

public record MemberResponse(Long id, String name) {
    public static MemberResponse from(Member member) {
        return new MemberResponse(member.getId(), member.getName());
    }
}
