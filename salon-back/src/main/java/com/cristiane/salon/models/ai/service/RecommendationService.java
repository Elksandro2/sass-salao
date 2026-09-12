package com.cristiane.salon.models.ai.service;

import com.cristiane.salon.config.SalonClock;
import com.cristiane.salon.exception.BusinessException;
import com.cristiane.salon.exception.ResourceNotFoundException;
import com.cristiane.salon.models.ai.client.OpenAiCompatibleChatClient;
import com.cristiane.salon.models.ai.client.ChatCompletionResult;
import com.cristiane.salon.models.ai.dto.RecommendationItem;
import com.cristiane.salon.models.ai.dto.RecommendationLlmResponse;
import com.cristiane.salon.models.ai.dto.RecommendationResult;
import com.cristiane.salon.models.ai.entity.AiCallLog;
import com.cristiane.salon.models.ai.entity.AiConfig;
import com.cristiane.salon.models.ai.entity.AiRecommendation;
import com.cristiane.salon.models.ai.entity.RecommendationType;
import com.cristiane.salon.models.ai.repository.AiCallLogRepository;
import com.cristiane.salon.models.ai.repository.AiRecommendationRepository;
import com.cristiane.salon.models.appointment.dto.AppointmentResponse;
import com.cristiane.salon.models.appointment.service.AppointmentService;
import com.cristiane.salon.models.featureflag.service.FeatureFlagService;
import com.cristiane.salon.models.report.dto.AppointmentReportResponse;
import com.cristiane.salon.models.report.dto.FinancialReportResponse;
import com.cristiane.salon.models.report.service.ReportService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationService {

    private static final int INACTIVITY_THRESHOLD_DAYS = 30;
    private static final String FEATURE_FLAG = "ENABLE_AI_RECOMMENDATIONS";

    private final AiConfigService aiConfigService;
    private final OpenAiCompatibleChatClient chatClient;
    private final ReportService reportService;
    private final AppointmentService appointmentService;
    private final AiRecommendationRepository recommendationRepository;
    private final AiCallLogRepository callLogRepository;
    private final FeatureFlagService featureFlagService;
    private final ObjectMapper objectMapper;
    private final SalonClock salonClock;

    // Sem @Transactional: o registro do log de chamada (logCall) precisa persistir mesmo quando
    // o método lança exceção (falha do provedor de IA) — uma transação única faria o rollback
    // também desfazer o log de falha, escondendo exatamente o evento que ele deveria registrar.
    public RecommendationResult generate(RecommendationType type, String callerType, String callerId) {
        requireFeatureEnabled();
        AiConfig config = aiConfigService.getDecryptedForInternalUse();

        if (!Boolean.TRUE.equals(config.getEnabled())) {
            throw new BusinessException("As recomendações de IA estão desativadas na Central de IA.");
        }
        String apiKey = aiConfigService.getDecryptedApiKey(config);
        if (apiKey == null || apiKey.isBlank()) {
            throw new BusinessException("Nenhuma API key configurada na Central de IA.");
        }

        long callsToday = callLogRepository.countSuccessfulSince(salonClock.today().atStartOfDay(salonClock.zone()).toInstant());
        if (callsToday >= config.getDailyCallBudget()) {
            throw new BusinessException("Orçamento diário de chamadas de IA atingido. Tente novamente amanhã ou aumente o limite na Central de IA.");
        }

        String userPrompt = switch (type) {
            case FINANCEIRO -> buildFinanceiroPrompt();
            case RETENCAO -> buildRetencaoPrompt();
        };

        long startedAt = System.currentTimeMillis();
        try {
            ChatCompletionResult completion = chatClient.complete(
                    config.getBaseUrl(),
                    apiKey,
                    config.getModel(),
                    config.getTemperature(),
                    config.getMaxTokens(),
                    RecommendationPromptBuilder.SYSTEM_PROMPT,
                    userPrompt
            );

            List<RecommendationItem> items = parseAndValidate(completion.content());
            int latencyMs = (int) (System.currentTimeMillis() - startedAt);

            Instant generatedAt = Instant.now();
            persistCache(type, items, generatedAt);
            logCall(callerType, callerId, type, completion.totalTokens(), latencyMs, true, null);

            return new RecommendationResult(type, items, generatedAt, false);
        } catch (BusinessException | ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            int latencyMs = (int) (System.currentTimeMillis() - startedAt);
            log.warn("Falha ao gerar recomendação {}: {}", type, e.getMessage());
            logCall(callerType, callerId, type, null, latencyMs, false, e.getMessage());
            throw new BusinessException("Falha ao consultar o provedor de IA. Tente novamente em instantes.");
        }
    }

    @Transactional(readOnly = true)
    public RecommendationResult getLatestCached(RecommendationType type) {
        requireFeatureEnabled();
        AiRecommendation cached = recommendationRepository.findFirstByTypeOrderByGeneratedAtDesc(type)
                .orElseThrow(() -> new ResourceNotFoundException("Nenhuma recomendação gerada ainda para " + type));
        List<RecommendationItem> items = deserialize(cached.getPayload());
        return new RecommendationResult(type, items, cached.getGeneratedAt(), true);
    }

    private void requireFeatureEnabled() {
        if (!featureFlagService.isEnabled(FEATURE_FLAG)) {
            throw new BusinessException("O módulo de recomendações de IA ainda não está disponível.");
        }
    }

    /**
     * Diagnóstico leve pra ADMIN/GERENTE saberem, antes de clicar em "Gerar", se
     * {@link #generate} vai funcionar agora — sem expor a configuração sensível
     * (URL, modelo, key), que é sysadmin-only. Não conta no orçamento diário nem
     * grava log, mesma lógica de {@code AiConfigService.testConnection}.
     */
    @Transactional(readOnly = true)
    public boolean isAvailable() {
        if (!featureFlagService.isEnabled(FEATURE_FLAG)) {
            return false;
        }
        AiConfig config = aiConfigService.getDecryptedForInternalUse();
        return Boolean.TRUE.equals(config.getEnabled());
    }

    private String buildFinanceiroPrompt() {
        LocalDate from = salonClock.today().minusDays(30);
        LocalDate to = salonClock.today();
        FinancialReportResponse financialReport = reportService.generateFinancialReport(from, to);
        AppointmentReportResponse appointmentReport = reportService.generateAppointmentReport(from, to);
        return RecommendationPromptBuilder.financeiroUserPrompt(
                toJson(financialReport), toJson(appointmentReport)
        );
    }

    // Bloco (scheduledDate/scheduledPeriod) não tem hora real, então usa início do dia — só
    // serve para saber "há quantos dias sem agendar", não a hora exata da última visita.
    private static LocalDateTime recommendationEffectiveDate(AppointmentResponse a) {
        if (a.scheduledAt() != null) return a.scheduledAt();
        if (a.scheduledDate() != null) return a.scheduledDate().atStartOfDay();
        return null;
    }

    private String buildRetencaoPrompt() {
        List<AppointmentResponse> appointments = appointmentService.findAllInternal();
        LocalDateTime now = salonClock.now();

        Map<String, LocalDateTime> lastVisitByClient = appointments.stream()
                .filter(a -> ("DONE".equals(a.status()) || "CONFIRMED".equals(a.status()))
                        && recommendationEffectiveDate(a) != null)
                .collect(Collectors.toMap(
                        AppointmentResponse::clientName,
                        RecommendationService::recommendationEffectiveDate,
                        (a, b) -> a.isAfter(b) ? a : b
                ));

        List<Map<String, Object>> inactivity = lastVisitByClient.entrySet().stream()
                .map(e -> Map.<String, Object>of(
                        "cliente", e.getKey(),
                        "diasSemAgendar", ChronoUnit.DAYS.between(e.getValue(), now)
                ))
                .filter(m -> (Long) m.get("diasSemAgendar") >= INACTIVITY_THRESHOLD_DAYS)
                .sorted(Comparator.comparingLong(m -> -(Long) m.get("diasSemAgendar")))
                .limit(30)
                .toList();

        if (inactivity.isEmpty()) {
            throw new BusinessException(
                "Nenhum cliente inativo há mais de " + INACTIVITY_THRESHOLD_DAYS +
                " dias encontrado. Não há dados suficientes para gerar recomendações de retenção."
            );
        }

        return RecommendationPromptBuilder.retencaoUserPrompt(toJson(inactivity));
    }

    private List<RecommendationItem> parseAndValidate(String content) {
        RecommendationLlmResponse parsed;
        try {
            parsed = objectMapper.readValue(content, RecommendationLlmResponse.class);
        } catch (Exception e) {
            throw new IllegalStateException("Resposta do modelo fora do formato JSON esperado", e);
        }

        List<RecommendationItem> items = parsed.recommendations();
        if (items == null || items.isEmpty()) {
            throw new IllegalStateException("Resposta do modelo sem recomendações");
        }
        for (RecommendationItem item : items) {
            if (item.title() == null || item.title().isBlank()
                    || item.description() == null || item.description().isBlank()
                    || item.priority() == null) {
                throw new IllegalStateException("Recomendação fora do schema esperado (campo obrigatório ausente)");
            }
        }
        return items;
    }

    private void persistCache(RecommendationType type, List<RecommendationItem> items, Instant generatedAt) {
        AiRecommendation recommendation = AiRecommendation.builder()
                .type(type)
                .payload(toJson(items))
                .generatedAt(generatedAt)
                .build();
        recommendationRepository.save(recommendation);
    }

    private void logCall(String callerType, String callerId, RecommendationType type, Integer tokensUsed,
                          int latencyMs, boolean success, String errorMessage) {
        AiCallLog logEntry = AiCallLog.builder()
                .callerType(callerType)
                .callerId(callerId)
                .callType(type.name())
                .tokensUsed(tokensUsed)
                .latencyMs(latencyMs)
                .success(success)
                .errorMessage(errorMessage)
                .createdAt(Instant.now())
                .build();
        callLogRepository.save(logEntry);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao serializar dado para o prompt de IA", e);
        }
    }

    private List<RecommendationItem> deserialize(String payload) {
        try {
            return objectMapper.readValue(payload, objectMapper.getTypeFactory()
                    .constructCollectionType(List.class, RecommendationItem.class));
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao ler recomendação em cache", e);
        }
    }
}
