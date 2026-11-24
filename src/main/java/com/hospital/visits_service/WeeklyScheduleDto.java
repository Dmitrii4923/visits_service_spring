package com.hospital.visits_service;

public record WeeklyScheduleDto(
        Integer dayOfWeek,
        String startTime,
        String endTime,
        Integer slotMinutes
) {
}
