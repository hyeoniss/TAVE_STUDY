package Tave_week2.assignment.member.service;

import Tave_week2.assignment.member.domain.Member;
import Tave_week2.assignment.member.dto.MemberCreateRequest;
import Tave_week2.assignment.member.dto.MemberResponse;
import Tave_week2.assignment.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {
    private final MemberRepository memberRepository;

    @Transactional
    public MemberResponse create(MemberCreateRequest request) {
        Member member = memberRepository.save(new Member(request.name()));
        return MemberResponse.from(member);
    }
}
