package com.hospital.visits_service;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/schedule")
public class ScheduleController {
    private final ScheduleService scheduleService;

    public ScheduleController(ScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    @GetMapping("/doctors/{doctorId}")
    public List<WeeklyScheduleDto> weekly(@PathVariable Long doctorId) {
        return scheduleService.weekly(doctorId);
    }

    @GetMapping("/doctors/{doctorId}/slots")
    public List<ScheduleSlotDto> slots(
            @PathVariable Long doctorId,
            @RequestParam String date) {
        return scheduleService.slots(doctorId, date);
    }

    @PutMapping("/doctors/{doctorId}")
    public List<WeeklyScheduleDto> update(
            @PathVariable Long doctorId,
            @RequestBody List<WeeklyScheduleDto> request,
            Authentication authentication) {
        return scheduleService.update(doctorId, request, authentication);
    }
}
