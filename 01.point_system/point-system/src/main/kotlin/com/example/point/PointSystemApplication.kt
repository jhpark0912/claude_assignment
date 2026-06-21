package com.example.point

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.data.jpa.repository.config.EnableJpaAuditing

@SpringBootApplication
@EnableJpaAuditing
class PointSystemApplication

fun main(args: Array<String>) {
    runApplication<PointSystemApplication>(*args)
}
