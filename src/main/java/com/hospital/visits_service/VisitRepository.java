package com.hospital.visits_service;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VisitRepository extends JpaRepository<Visit, Long> {
    List<Visit> findByPatientIdOrderByDateTimeDesc(Long patientId);
    List<Visit> findByDoctorIdOrderByDateTimeDesc(Long doctorId);
    List<Visit> findByDoctorIdAndDateTimeStartingWith(Long doctorId, String datePrefix);
    boolean existsByDoctorIdAndDateTimeAndStatusNot(Long doctorId, String dateTime, VisitStatus status);
    Optional<Visit> findByIdAndPatientId(Long id, Long patientId);
    Optional<Visit> findByIdAndDoctorId(Long id, Long doctorId);
}
