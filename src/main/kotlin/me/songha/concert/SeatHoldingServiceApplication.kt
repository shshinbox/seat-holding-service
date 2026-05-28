package me.songha.concert

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@EnableScheduling
@SpringBootApplication
class SeatHoldingServiceApplication

fun main(args: Array<String>) {
    runApplication<SeatHoldingServiceApplication>(*args)
}
