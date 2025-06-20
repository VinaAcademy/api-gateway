package vn.huuloc.apigateway.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.vinaacademy.common.response.ApiResponse;

@RestController
@Slf4j
@RequestMapping("/fallback")
public class FallbackController {

    @GetMapping("/general")
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ApiResponse<?> generalFallback() {
        log.error("General fallback triggered");
        return ApiResponse.error("Service is currently unavailable. Please try again later.");
    }
}
