package edu.ldubgd.adaptivemanager

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class ObservationController(private val observation: ObservationService) {
    @GetMapping("/observations")
    fun observations() = observation.snapshot()
}
