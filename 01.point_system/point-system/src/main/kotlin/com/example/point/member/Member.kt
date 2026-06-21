package com.example.point.member

import jakarta.persistence.*
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.LocalDateTime

@Entity
@Table(name = "member")
@EntityListeners(AuditingEntityListener::class)
class Member(
    @Id
    @Column(length = 50)
    val userId: String,

    @Column(nullable = false, length = 100)
    val username: String,

    @Column(nullable = false, length = 200)
    val password: String,

    @CreatedDate
    @Column(nullable = false, updatable = false)
    var createdAt: LocalDateTime = LocalDateTime.now(),

    @LastModifiedDate
    @Column(nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
) {
    companion object {
        fun of(userId: String, username: String, password: String) =
            Member(userId = userId, username = username, password = password)
    }
}
