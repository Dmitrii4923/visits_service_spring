package com.hospital.visits_service;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/visits")
public class VisitController {
    private final VisitService visitService;

    public VisitController(VisitService visitService) {
        this.visitService = visitService;
    }

    @GetMapping
    public List<VisitDto> getAll(
            @RequestParam(required = false) String date,
            Authentication authentication) {
        return visitService.list(date, authentication);
    }

    @GetMapping("/{id}")
    public VisitDto getById(@PathVariable Long id, Authentication authentication) {
        return visitService.get(id, authentication);
    }

    @PostMapping
    public VisitDto create(@RequestBody VisitRequest request, Authentication authentication) {
        return visitService.create(request, authentication);
    }

    @PostMapping("/{id}/cancel")
    public VisitDto cancel(@PathVariable Long id, Authentication authentication) {
        return visitService.cancel(id, authentication);
    }

    @PutMapping("/{id}/diagnosis")
    public VisitDto setDiagnosis(
            @PathVariable Long id,
            @RequestBody String diagnosis,
            Authentication authentication) {
        return visitService.setDiagnosis(id, diagnosis, authentication);
    }

    @PutMapping("/{id}/record")
    public VisitDto saveRecord(
            @PathVariable Long id,
            @RequestBody MedicalRecordRequest request,
            Authentication authentication) {
        return visitService.saveRecord(id, request, authentication);
    }
}
