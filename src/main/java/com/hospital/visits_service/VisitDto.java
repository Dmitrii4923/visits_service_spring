package com.hospital.visits_service;

import java.time.LocalDateTime;

public record VisitDto(
        Long id,
        Long patientId,
        Long doctorId,
        String dateTime,
        String diagnosis,
        VisitStatus status,
        LocalDateTime completedAt,
        PatientDto patient,
        DoctorDto doctor,
        MedicalRecord record
) {
}
