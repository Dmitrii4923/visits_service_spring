package com.hospital.visits_service;

public record MedicalRecordRequest(
        String complaints,
        String anamnesis,
        String examination,
        String diagnosis,
        String treatment,
        String recommendations
) {
}
