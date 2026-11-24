package com.hospital.visits_service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class VisitService {
    private static final Logger log = LoggerFactory.getLogger(VisitService.class);

    private final VisitRepository visitRepository;
    private final MedicalRecordRepository medicalRecordRepository;
    private final IdentityService identityService;
    private final ScheduleService scheduleService;

    public VisitService(
            VisitRepository visitRepository,
            MedicalRecordRepository medicalRecordRepository,
            IdentityService identityService,
            ScheduleService scheduleService) {
        this.visitRepository = visitRepository;
        this.medicalRecordRepository = medicalRecordRepository;
        this.identityService = identityService;
        this.scheduleService = scheduleService;
    }

    public List<VisitDto> list(String date, Authentication authentication) {
        log.debug("Запрошен список приёмов: date={}", date);

        if (identityService.hasRole(authentication, "ADMIN")) {
            return visitRepository.findAll().stream()
                    .filter(visit -> matchesDate(visit, date))
                    .sorted((left, right) -> String.valueOf(right.getDateTime())
                            .compareTo(String.valueOf(left.getDateTime())))
                    .map(visit -> toDto(visit, null, null))
                    .toList();
        }

        if (identityService.hasRole(authentication, "DOCTOR")) {
            DoctorDto doctor = identityService.currentDoctor();
            return visitRepository.findByDoctorIdOrderByDateTimeDesc(doctor.id()).stream()
                    .filter(visit -> matchesDate(visit, date))
                    .map(visit -> toDto(visit, null, doctor))
                    .toList();
        }

        if (identityService.hasRole(authentication, "PATIENT")) {
            PatientDto patient = identityService.currentPatient();
            return visitRepository.findByPatientIdOrderByDateTimeDesc(patient.id()).stream()
                    .filter(visit -> matchesDate(visit, date))
                    .map(visit -> toDto(visit, patient, null))
                    .toList();
        }

        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Недостаточно прав");
    }

    public VisitDto get(Long id, Authentication authentication) {
        log.debug("Запрошен приём: id={}", id);

        if (identityService.hasRole(authentication, "ADMIN")) {
            Visit visit = visitRepository.findById(id)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND, "Запись не найдена"));
            return toDto(visit, null, null);
        }

        if (identityService.hasRole(authentication, "DOCTOR")) {
            DoctorDto doctor = identityService.currentDoctor();
            Visit visit = visitRepository.findByIdAndDoctorId(id, doctor.id())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Запись не найдена"));
            return toDto(visit, null, doctor);
        }

        if (identityService.hasRole(authentication, "PATIENT")) {
            PatientDto patient = identityService.currentPatient();
            Visit visit = visitRepository.findByIdAndPatientId(id, patient.id())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Запись не найдена"));
            return toDto(visit, patient, null);
        }

        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Недостаточно прав");
    }

    @Transactional
    public VisitDto create(VisitRequest request, Authentication authentication) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Заполните данные записи");
        }

        Long patientId;
        Long doctorId;
        PatientDto patient = null;
        DoctorDto doctor = null;

        if (identityService.hasRole(authentication, "DOCTOR")) {
            doctor = identityService.currentDoctor();
            doctorId = doctor.id();

            if (request.patientId() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Укажите пациента");
            }

            patientId = request.patientId();
            patient = identityService.patient(patientId);
        } else if (identityService.hasRole(authentication, "PATIENT")) {
            patient = identityService.currentPatient();
            patientId = patient.id();

            if (request.doctorId() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Укажите врача");
            }

            doctorId = request.doctorId();
            doctor = identityService.doctor(doctorId);
        } else {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Недостаточно прав");
        }

        String dateTime = scheduleService.normalize(request.dateTime());
        scheduleService.ensureAvailable(doctorId, dateTime);

        log.info("Создание приёма: patientId={}, doctorId={}, dateTime={}", patientId, doctorId, dateTime);

        if (visitRepository.existsByDoctorIdAndDateTimeAndStatusNot(doctorId, dateTime, VisitStatus.CANCELLED)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Это время уже занято");
        }

        Visit visit = new Visit();
        visit.setPatientId(patientId);
        visit.setDoctorId(doctorId);
        visit.setDateTime(dateTime);
        visit.setStatus(VisitStatus.BOOKED);

        try {
            visit = visitRepository.save(visit);
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Это время уже занято");
        }

        return toDto(visit, patient, doctor);
    }

    @Transactional
    public VisitDto setDiagnosis(Long id, String diagnosis, Authentication authentication) {
        log.info("Установка диагноза: visitId={}", id);
        DoctorDto doctor = identityService.currentDoctor();
        Visit visit = visitRepository.findByIdAndDoctorId(id, doctor.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Запись не найдена"));

        if (diagnosis == null || diagnosis.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Введите диагноз");
        }

        visit.setDiagnosis(diagnosis.trim());
        visit = visitRepository.save(visit);
        return toDto(visit, null, doctor);
    }

    @Transactional
    public VisitDto saveRecord(Long id, MedicalRecordRequest request, Authentication authentication) {
        log.info("Сохранение медицинской карты: visitId={}", id);
        DoctorDto doctor = identityService.currentDoctor();
        Visit visit = visitRepository.findByIdAndDoctorId(id, doctor.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Запись не найдена"));

        if (request == null || request.diagnosis() == null || request.diagnosis().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Заполните диагноз");
        }

        MedicalRecord record = medicalRecordRepository.findByVisitId(id).orElseGet(MedicalRecord::new);
        record.setVisitId(id);
        record.setComplaints(trim(request.complaints()));
        record.setAnamnesis(trim(request.anamnesis()));
        record.setExamination(trim(request.examination()));
        record.setDiagnosis(request.diagnosis().trim());
        record.setTreatment(trim(request.treatment()));
        record.setRecommendations(trim(request.recommendations()));
        record.setRecordedAt(LocalDateTime.now());
        medicalRecordRepository.save(record);

        visit.setDiagnosis(request.diagnosis().trim());
        visit.setStatus(VisitStatus.COMPLETED);
        visit.setCompletedAt(record.getRecordedAt());
        visit = visitRepository.save(visit);

        return toDto(visit, null, doctor);
    }

    @Transactional
    public VisitDto cancel(Long id, Authentication authentication) {
        log.info("Отмена приёма: visitId={}", id);
        Visit visit;
        PatientDto patient = null;
        DoctorDto doctor = null;

        if (identityService.hasRole(authentication, "ADMIN")) {
            visit = visitRepository.findById(id)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND, "Запись не найдена"));
        } else if (identityService.hasRole(authentication, "DOCTOR")) {
            doctor = identityService.currentDoctor();
            Long doctorId = doctor.id();
            visit = visitRepository.findByIdAndDoctorId(id, doctorId)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND, "Запись не найдена"));
        } else if (identityService.hasRole(authentication, "PATIENT")) {
            patient = identityService.currentPatient();
            Long patientId = patient.id();
            visit = visitRepository.findByIdAndPatientId(id, patientId)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND, "Запись не найдена"));
        } else {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Недостаточно прав");
        }

        if (visit.getStatus() == VisitStatus.COMPLETED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Завершённый приём нельзя отменить");
        }

        if (visit.getStatus() != VisitStatus.CANCELLED) {
            visit.setStatus(VisitStatus.CANCELLED);
            visit.setCompletedAt(null);
            visit = visitRepository.save(visit);
        }

        return toDto(visit, patient, doctor);
    }

    private boolean matchesDate(Visit visit, String date) {
        return date == null || date.isBlank() || visit.getDateTime().startsWith(date);
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }

    private VisitDto toDto(Visit visit, PatientDto patient, DoctorDto doctor) {
        PatientDto resolvedPatient = patient;
        DoctorDto resolvedDoctor = doctor;
        MedicalRecord record = null;

        try {
            resolvedPatient = patient != null
                    ? patient
                    : identityService.patientOrNull(visit.getPatientId());
        } catch (Exception ignored) {
            resolvedPatient = null;
        }

        try {
            resolvedDoctor = doctor != null
                    ? doctor
                    : identityService.doctorOrNull(visit.getDoctorId());
        } catch (Exception ignored) {
            resolvedDoctor = null;
        }

        try {
            record = medicalRecordRepository.findByVisitId(visit.getId()).orElse(null);
        } catch (Exception ignored) {
            record = null;
        }

        VisitStatus status = visit.getStatus() == null ? VisitStatus.BOOKED : visit.getStatus();

        return new VisitDto(
                visit.getId(),
                visit.getPatientId(),
                visit.getDoctorId(),
                visit.getDateTime(),
                visit.getDiagnosis(),
                status,
                visit.getCompletedAt(),
                resolvedPatient,
                resolvedDoctor,
                record);
    }
}
