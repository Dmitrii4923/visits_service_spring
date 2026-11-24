package com.hospital.visits_service;

import feign.FeignException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class IdentityService {
    private static final Logger log = LoggerFactory.getLogger(IdentityService.class);

    private static final String PATIENT_SERVICE = "patientService";
    private static final String DOCTOR_SERVICE = "doctorService";
    private static final String PATIENT_SERVICE_MESSAGE = "Сервис пациентов временно недоступен";
    private static final String DOCTOR_SERVICE_MESSAGE = "Сервис врачей временно недоступен";

    private final PatientClient patientClient;
    private final DoctorClient doctorClient;

    public IdentityService(PatientClient patientClient, DoctorClient doctorClient) {
        this.patientClient = patientClient;
        this.doctorClient = doctorClient;
    }

    public boolean hasRole(Authentication authentication, String role) {
        String authority = "ROLE_" + role;
        return authentication.getAuthorities().stream()
                .anyMatch(item -> authority.equals(item.getAuthority()));
    }

    @CircuitBreaker(name = PATIENT_SERVICE, fallbackMethod = "currentPatientFallback")
    public PatientDto currentPatient() {
        try {
            return patientClient.getCurrentPatient();
        } catch (FeignException.NotFound | FeignException.Forbidden exception) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Профиль пациента не привязан к учётной записи");
        } catch (FeignException exception) {
            log.warn("Ошибка вызова customer-service при получении текущего пациента: {}", exception.getMessage());
            throw new RemoteServiceUnavailableException(PATIENT_SERVICE_MESSAGE, exception);
        }
    }

    @CircuitBreaker(name = DOCTOR_SERVICE, fallbackMethod = "currentDoctorFallback")
    public DoctorDto currentDoctor() {
        try {
            return doctorClient.getCurrentDoctor();
        } catch (FeignException.NotFound | FeignException.Forbidden exception) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Профиль врача не привязан к учётной записи");
        } catch (FeignException exception) {
            log.warn("Ошибка вызова vets-service при получении текущего врача: {}", exception.getMessage());
            throw new RemoteServiceUnavailableException(DOCTOR_SERVICE_MESSAGE, exception);
        }
    }

    @CircuitBreaker(name = DOCTOR_SERVICE, fallbackMethod = "doctorFallback")
    public DoctorDto doctor(Long id) {
        try {
            return doctorClient.getDoctorById(id);
        } catch (FeignException.NotFound exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Врач не найден");
        } catch (FeignException exception) {
            log.warn("Ошибка вызова vets-service при получении врача id={}: {}", id, exception.getMessage());
            throw new RemoteServiceUnavailableException(DOCTOR_SERVICE_MESSAGE, exception);
        }
    }

    @CircuitBreaker(name = PATIENT_SERVICE, fallbackMethod = "patientFallback")
    public PatientDto patient(Long id) {
        try {
            return patientClient.getPatientById(id);
        } catch (FeignException.NotFound exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Пациент не найден");
        } catch (FeignException exception) {
            log.warn("Ошибка вызова customer-service при получении пациента id={}: {}", id, exception.getMessage());
            throw new RemoteServiceUnavailableException(PATIENT_SERVICE_MESSAGE, exception);
        }
    }

    @CircuitBreaker(name = PATIENT_SERVICE, fallbackMethod = "patientOrNullFallback")
    public PatientDto patientOrNull(Long id) {
        try {
            return patientClient.getPatientById(id);
        } catch (FeignException exception) {
            log.debug("Не удалось получить пациента id={} для отображения: {}", id, exception.getMessage());
            return null;
        }
    }

    @CircuitBreaker(name = DOCTOR_SERVICE, fallbackMethod = "doctorOrNullFallback")
    public DoctorDto doctorOrNull(Long id) {
        try {
            return doctorClient.getDoctorById(id);
        } catch (FeignException exception) {
            log.debug("Не удалось получить врача id={} для отображения: {}", id, exception.getMessage());
            return null;
        }
    }

    private PatientDto currentPatientFallback(Throwable throwable) {
        throw fallbackException(PATIENT_SERVICE_MESSAGE, throwable);
    }

    private DoctorDto currentDoctorFallback(Throwable throwable) {
        throw fallbackException(DOCTOR_SERVICE_MESSAGE, throwable);
    }

    private DoctorDto doctorFallback(Long id, Throwable throwable) {
        throw fallbackException(DOCTOR_SERVICE_MESSAGE, throwable);
    }

    private PatientDto patientFallback(Long id, Throwable throwable) {
        throw fallbackException(PATIENT_SERVICE_MESSAGE, throwable);
    }

    private PatientDto patientOrNullFallback(Long id, Throwable throwable) {
        log.warn("Circuit Breaker patientService: не удалось получить пациента id={} — {}", id,
                throwable.getMessage());
        return null;
    }

    private DoctorDto doctorOrNullFallback(Long id, Throwable throwable) {
        log.warn("Circuit Breaker doctorService: не удалось получить врача id={} — {}", id,
                throwable.getMessage());
        return null;
    }

    private RuntimeException fallbackException(String serviceMessage, Throwable throwable) {
        if (throwable instanceof ResponseStatusException responseStatusException) {
            return responseStatusException;
        }

        if (throwable instanceof FeignException.NotFound || throwable instanceof FeignException.Forbidden) {
            return new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Профиль не привязан к учётной записи");
        }

        log.error("Circuit Breaker сработал: {}", serviceMessage, throwable);
        return new RemoteServiceUnavailableException(serviceMessage, throwable);
    }
}
