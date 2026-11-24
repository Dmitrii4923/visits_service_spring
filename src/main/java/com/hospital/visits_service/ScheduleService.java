package com.hospital.visits_service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ScheduleService {
    private static final Logger log = LoggerFactory.getLogger(ScheduleService.class);

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final DoctorScheduleRepository scheduleRepository;
    private final VisitRepository visitRepository;
    private final IdentityService identityService;

    public ScheduleService(
            DoctorScheduleRepository scheduleRepository,
            VisitRepository visitRepository,
            IdentityService identityService) {
        this.scheduleRepository = scheduleRepository;
        this.visitRepository = visitRepository;
        this.identityService = identityService;
    }

    public List<WeeklyScheduleDto> weekly(Long doctorId) {
        log.debug("Запрос недельного расписания: doctorId={}", doctorId);
        identityService.doctor(doctorId);
        ensureDefaults(doctorId);

        return toWeekly(scheduleRepository.findByDoctorIdOrderByDayOfWeekAsc(doctorId));
    }

    @Transactional
    public List<WeeklyScheduleDto> update(
            Long doctorId,
            List<WeeklyScheduleDto> request,
            Authentication authentication) {
        log.info("Обновление расписания: doctorId={}, дней={}", doctorId,
                request == null ? 0 : request.size());

        if (identityService.hasRole(authentication, "DOCTOR")) {
            DoctorDto current = identityService.currentDoctor();
            if (!current.id().equals(doctorId)) {
                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN, "Можно редактировать только собственное расписание");
            }
        } else if (!identityService.hasRole(authentication, "ADMIN")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Недостаточно прав");
        }

        identityService.doctor(doctorId);

        if (request == null || request.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Добавьте хотя бы один рабочий день");
        }

        Set<Integer> seenDays = new HashSet<>();
        List<DoctorSchedule> schedules = new ArrayList<>();

        for (WeeklyScheduleDto item : request) {
            if (item == null || item.dayOfWeek() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Укажите день недели");
            }

            int day = item.dayOfWeek();
            if (day < 1 || day > 7) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "День недели должен быть от 1 (понедельник) до 7 (воскресенье)");
            }

            if (!seenDays.add(day)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "День недели указан дважды");
            }

            LocalTime start = parseTime(item.startTime(), "Начало приёма");
            LocalTime end = parseTime(item.endTime(), "Окончание приёма");

            if (!start.isBefore(end)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Начало приёма должно быть раньше окончания");
            }

            int slotMinutes = item.slotMinutes() == null ? 30 : item.slotMinutes();
            if (slotMinutes < 5 || slotMinutes > 240) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Длительность слота должна быть от 5 до 240 минут");
            }

            schedules.add(schedule(doctorId, day, start, end, slotMinutes));
        }

        List<DoctorSchedule> existing =
                scheduleRepository.findByDoctorIdOrderByDayOfWeekAsc(doctorId);
        if (!existing.isEmpty()) {
            scheduleRepository.deleteAllInBatch(existing);
        }

        schedules.sort(Comparator.comparing(DoctorSchedule::getDayOfWeek));
        scheduleRepository.saveAll(schedules);

        return toWeekly(schedules);
    }

    public List<ScheduleSlotDto> slots(Long doctorId, String dateText) {
        log.debug("Запрос слотов расписания: doctorId={}, date={}", doctorId, dateText);
        LocalDate date = parseDate(dateText);
        identityService.doctor(doctorId);
        ensureDefaults(doctorId);

        DoctorSchedule schedule = scheduleRepository
                .findByDoctorIdAndDayOfWeek(doctorId, date.getDayOfWeek().getValue())
                .orElse(null);

        if (schedule == null) {
            return List.of();
        }

        Set<String> booked = visitRepository
                .findByDoctorIdAndDateTimeStartingWith(doctorId, dateText + " ")
                .stream()
                .filter(visit -> visit.getStatus() != VisitStatus.CANCELLED)
                .map(Visit::getDateTime)
                .collect(Collectors.toSet());

        List<ScheduleSlotDto> result = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        LocalTime time = schedule.getStartTime();

        while (time.isBefore(schedule.getEndTime())) {
            String label = TIME_FORMAT.format(time);
            String full = dateText + " " + label;
            boolean past = LocalDateTime.of(date, time).isBefore(now);
            boolean busy = booked.contains(full);
            result.add(new ScheduleSlotDto(label, !past && !busy));
            time = time.plusMinutes(schedule.getSlotMinutes());
        }

        return result;
    }

    public String normalize(String value) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Укажите дату и время приёма");
        }

        String text = value.trim().replace('T', ' ');
        LocalDateTime dateTime;

        try {
            dateTime = LocalDateTime.parse(text, DATE_TIME_FORMAT);
        } catch (DateTimeParseException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Неверный формат даты и времени, ожидается yyyy-MM-dd HH:mm");
        }

        return DATE_TIME_FORMAT.format(dateTime);
    }

    public void ensureAvailable(Long doctorId, String dateTime) {
        LocalDateTime moment = LocalDateTime.parse(dateTime, DATE_TIME_FORMAT);

        if (moment.isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Нельзя записаться на прошедшее время");
        }

        String date = DATE_FORMAT.format(moment.toLocalDate());
        String time = TIME_FORMAT.format(moment.toLocalTime());

        boolean available = slots(doctorId, date).stream()
                .anyMatch(slot -> slot.time().equals(time) && slot.available());

        if (!available) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Это время уже занято или недоступно");
        }
    }

    private List<WeeklyScheduleDto> toWeekly(List<DoctorSchedule> items) {
        return items.stream()
                .map(item -> new WeeklyScheduleDto(
                        item.getDayOfWeek(),
                        TIME_FORMAT.format(item.getStartTime()),
                        TIME_FORMAT.format(item.getEndTime()),
                        item.getSlotMinutes()))
                .toList();
    }

    private LocalTime parseTime(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + ": укажите время");
        }

        try {
            return LocalTime.parse(value.trim(), TIME_FORMAT);
        } catch (DateTimeParseException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, field + ": ожидается формат HH:mm");
        }
    }

    private DoctorSchedule schedule(
            Long doctorId,
            int day,
            LocalTime start,
            LocalTime end,
            int slotMinutes) {
        DoctorSchedule schedule = new DoctorSchedule();
        schedule.setDoctorId(doctorId);
        schedule.setDayOfWeek(day);
        schedule.setStartTime(start);
        schedule.setEndTime(end);
        schedule.setSlotMinutes(slotMinutes);
        return schedule;
    }

    private void ensureDefaults(Long doctorId) {
        if (scheduleRepository.existsByDoctorId(doctorId)) {
            return;
        }

        List<DoctorSchedule> schedules = new ArrayList<>();
        for (int day = 1; day <= 5; day++) {
            schedules.add(defaultSchedule(doctorId, day, LocalTime.of(9, 0), LocalTime.of(18, 0)));
        }
        schedules.add(defaultSchedule(doctorId, 6, LocalTime.of(9, 0), LocalTime.of(14, 0)));

        log.info("Создание расписания по умолчанию: doctorId={}", doctorId);

        try {
            scheduleRepository.saveAll(schedules);
        } catch (DataIntegrityViolationException ignored) {
        }
    }

    private DoctorSchedule defaultSchedule(Long doctorId, int day, LocalTime start, LocalTime end) {
        DoctorSchedule schedule = new DoctorSchedule();
        schedule.setDoctorId(doctorId);
        schedule.setDayOfWeek(day);
        schedule.setStartTime(start);
        schedule.setEndTime(end);
        schedule.setSlotMinutes(30);
        return schedule;
    }

    private LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(value, DATE_FORMAT);
        } catch (DateTimeParseException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Неверный формат даты, ожидается yyyy-MM-dd");
        }
    }
}
