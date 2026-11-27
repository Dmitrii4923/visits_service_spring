package com.hospital.visits_service;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "customer-service", url = "${CUSTOMER_URL:}", configuration = FeignConfig.class)
public interface PatientClient {
    @GetMapping("/api/customers/{id}")
    PatientDto getPatientById(@PathVariable("id") Long id);

    @GetMapping("/api/customers/me")
    PatientDto getCurrentPatient();
}
