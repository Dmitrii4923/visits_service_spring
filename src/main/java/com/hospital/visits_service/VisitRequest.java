package com.hospital.visits_service;

public record VisitRequest(Long patientId, Long doctorId, String dateTime) {
}
