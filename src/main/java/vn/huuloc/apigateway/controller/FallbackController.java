package vn.huuloc.apigateway.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Slf4j
@RequestMapping("/fallback")
public class FallbackController {

    @GetMapping("/general")
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ResponseEntity<?> generalFallback() {
        log.error("General fallback triggered");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body("Service is currently unavailable. Please try again later.");
    }
}
