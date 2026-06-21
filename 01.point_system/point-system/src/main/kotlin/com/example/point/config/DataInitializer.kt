package com.example.point.config

import com.example.point.member.Member
import com.example.point.member.MemberRepository
import org.springframework.boot.CommandLineRunner
import org.springframework.stereotype.Component

@Component
class DataInitializer(private val memberRepository: MemberRepository) : CommandLineRunner {

    override fun run(vararg args: String?) {
        val members = listOf(
            Member.of("user-1", "Alice", "hashed_pw_1"),
            Member.of("user-2", "Bob", "hashed_pw_2"),
            Member.of("user-3", "Charlie", "hashed_pw_3")
        )
        memberRepository.saveAll(members)
    }
}
