package com.cristiane.salon.models.appointment.service;

import com.cristiane.salon.integrations.email.service.EmailService;
import com.cristiane.salon.integrations.push.service.PushService;
import com.cristiane.salon.models.appointment.entity.Appointment;
import com.cristiane.salon.models.appointment.repository.AppointmentRepository;
import com.cristiane.salon.models.featureflag.service.FeatureFlagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.time.Instant;

/**
 * Lembrete de agendamento D-1 (issue #111): reduz no-show avisando o cliente na véspera.
 *
 * <p>"Amanhã" é calculado no fuso do negócio (America/Recife), não no fuso padrão da JVM (UTC,
 * setado em {@code SalonApplication.init()}) — calcular com {@code LocalDate.now()} puro erraria
 * o dia perto da meia-noite (Recife está 3h atrás de UTC).
 *
 * <p>{@code remindedAt} é gravado logo após disparar o e-mail para CADA agendamento
 * individualmente (não em lote no fim do job) — se o processo cair no meio da execução, os
 * agendamentos já processados não são notificados de novo no próximo disparo do job.
 *
 * <p>Dispara tanto e-mail quanto push (issue #110) — os dois canais fazem sentido pro mesmo
 * lembrete; push só chega de fato a quem autorizou notificações e tem o PWA instalado.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppointmentReminderService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("America/Recife");

    private final AppointmentRepository appointmentRepository;
    private final EmailService emailService;
    private final PushService pushService;
    private final FeatureFlagService featureFlagService;

    @Scheduled(cron = "${app.appointment-reminder.cron:0 0 9 * * *}", zone = "America/Recife")
    @Transactional
    public void sendDailyReminders() {
        if (!featureFlagService.isEnabled("EMAIL_NOTIFICATIONS")) {
            log.info("Lembrete de agendamento (D-1) desativado por Feature Flag (EMAIL_NOTIFICATIONS).");
            return;
        }

        LocalDate tomorrow = LocalDate.now(BUSINESS_ZONE).plusDays(1);
        LocalDateTime startOfDay = tomorrow.atStartOfDay();
        LocalDateTime endOfDay = tomorrow.plusDays(1).atStartOfDay();

        List<Appointment> eligible = new ArrayList<>(
                appointmentRepository.findConfirmedNotRemindedBetween(startOfDay, endOfDay));
        eligible.addAll(appointmentRepository.findConfirmedNotRemindedOnDate(tomorrow));
        log.info("Lembrete de agendamento (D-1): {} agendamento(s) elegível(is) para {}", eligible.size(), tomorrow);

        for (Appointment appointment : eligible) {
            emailService.sendAppointmentReminder(appointment);
            pushService.sendToUser(appointment.getClient().getId(), "Seu agendamento é amanhã! ⏰",
                    "Não esqueça: " + appointment.getServiceNames() + " agendado para amanhã.", "/my-appointments");
            appointment.setRemindedAt(Instant.now());
            appointmentRepository.save(appointment);
        }
    }
}
