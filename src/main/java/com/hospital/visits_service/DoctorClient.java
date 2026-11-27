package com.hospital.visits_service;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "vets-service", url = "${VETS_URL:}", configuration = FeignConfig.class)
public interface DoctorClient {
    @GetMapping("/api/vets/{id}")
    DoctorDto getDoctorById(@PathVariable("id") Long id);

    @GetMapping("/api/vets/me")
    DoctorDto getCurrentDoctor();
}
