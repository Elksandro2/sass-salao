package com.cristiane.salon.aspect;

import com.cristiane.salon.annotation.Auditable;
import com.cristiane.salon.models.appointment.dto.AppointmentResponse;
import com.cristiane.salon.models.audit.AuditLogService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.Authentication;
import com.cristiane.salon.models.user.entity.User;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditAspectTest {

    @Mock
    private AuditLogService auditLogService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private AuditAspect auditAspect;

    @BeforeEach
    void setUp() {
        auditAspect = new AuditAspect(auditLogService, objectMapper);
    }

    public void dummyUpdateMethod(@PathVariable Long id, String status) {}
    public void dummyUpdateMethodStringPath(@PathVariable String id) {}
    public void dummyCreateMethod() {}

    @Test
    void testExtractEntityIdFromPathVariable() throws Exception {
        JoinPoint joinPoint = mock(JoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        
        Method method = this.getClass().getMethod("dummyUpdateMethod", Long.class, String.class);
        when(signature.getMethod()).thenReturn(method);
        when(joinPoint.getSignature()).thenReturn(signature);
        
        Object[] args = new Object[]{ 42L, "PENDING" };
        when(joinPoint.getArgs()).thenReturn(args);

        Auditable auditable = mock(Auditable.class);
        when(auditable.action()).thenReturn("UPDATE");
        when(auditable.entityType()).thenReturn("Dummy");
        when(auditable.captureArgs()).thenReturn(false);

        auditAspect.logSuccessfulAction(joinPoint, auditable, null);

        verify(auditLogService).logAction(
                any(), any(), eq("UPDATE"), eq("Dummy"), eq(42L), any(), eq("SUCCESS")
        );
    }

    @Test
    void testExtractEntityIdFromPathVariableString() throws Exception {
        JoinPoint joinPoint = mock(JoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        
        Method method = this.getClass().getMethod("dummyUpdateMethodStringPath", String.class);
        when(signature.getMethod()).thenReturn(method);
        when(joinPoint.getSignature()).thenReturn(signature);
        
        Object[] args = new Object[]{ "100" };
        when(joinPoint.getArgs()).thenReturn(args);

        Auditable auditable = mock(Auditable.class);
        when(auditable.action()).thenReturn("UPDATE");
        when(auditable.entityType()).thenReturn("Dummy");

        auditAspect.logSuccessfulAction(joinPoint, auditable, null);

        verify(auditLogService).logAction(
                any(), any(), eq("UPDATE"), eq("Dummy"), eq(100L), any(), eq("SUCCESS")
        );
    }

    @Test
    void testExtractEntityIdFromPathVariableStringInvalid() throws Exception {
        JoinPoint joinPoint = mock(JoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        
        Method method = this.getClass().getMethod("dummyUpdateMethodStringPath", String.class);
        when(signature.getMethod()).thenReturn(method);
        when(joinPoint.getSignature()).thenReturn(signature);
        
        Object[] args = new Object[]{ "abc" };
        when(joinPoint.getArgs()).thenReturn(args);

        Auditable auditable = mock(Auditable.class);
        when(auditable.action()).thenReturn("UPDATE");
        when(auditable.entityType()).thenReturn("Dummy");

        auditAspect.logSuccessfulAction(joinPoint, auditable, null);

        verify(auditLogService).logAction(
                any(), any(), eq("UPDATE"), eq("Dummy"), isNull(), any(), eq("SUCCESS")
        );
    }

    @Test
    void testExtractEntityIdFromReturnValue() throws Exception {
        JoinPoint joinPoint = mock(JoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        
        Method method = this.getClass().getMethod("dummyCreateMethod");
        when(signature.getMethod()).thenReturn(method);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getArgs()).thenReturn(new Object[0]);

        AppointmentResponse response = new AppointmentResponse(
                99L, 1L, "Client", 2L, "Employee", java.util.List.of(), null, null,
                null, null, "Notes", null, "PENDING", null, null, null, false, ""
        );
        ResponseEntity<AppointmentResponse> result = ResponseEntity.ok(response);

        Auditable auditable = mock(Auditable.class);
        when(auditable.action()).thenReturn("CREATE");
        when(auditable.entityType()).thenReturn("Dummy");
        
        auditAspect.logSuccessfulAction(joinPoint, auditable, result);

        verify(auditLogService).logAction(
                any(), any(), eq("CREATE"), eq("Dummy"), eq(99L), any(), eq("SUCCESS")
        );
    }

    @Test
    void testExtractEntityIdFromReturnValueNumber() throws Exception {
        JoinPoint joinPoint = mock(JoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        Method method = this.getClass().getMethod("dummyCreateMethod");
        when(signature.getMethod()).thenReturn(method);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getArgs()).thenReturn(new Object[0]);

        Auditable auditable = mock(Auditable.class);
        when(auditable.action()).thenReturn("CREATE");
        when(auditable.entityType()).thenReturn("Dummy");
        
        auditAspect.logSuccessfulAction(joinPoint, auditable, 77L);

        verify(auditLogService).logAction(
                any(), any(), eq("CREATE"), eq("Dummy"), eq(77L), any(), eq("SUCCESS")
        );
    }

    @Test
    void testExtractEntityIdFromRecordStyleId() throws Exception {
        JoinPoint joinPoint = mock(JoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        Method method = this.getClass().getMethod("dummyCreateMethod");
        when(signature.getMethod()).thenReturn(method);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getArgs()).thenReturn(new Object[0]);

        class RecordDummy {
            public Long id() { return 123L; }
        }

        Auditable auditable = mock(Auditable.class);
        when(auditable.action()).thenReturn("CREATE");
        when(auditable.entityType()).thenReturn("Dummy");

        auditAspect.logSuccessfulAction(joinPoint, auditable, new RecordDummy());

        verify(auditLogService).logAction(
                any(), any(), eq("CREATE"), eq("Dummy"), eq(123L), any(), eq("SUCCESS")
        );
    }

    @Test
    void testStatusOverrideToCompleted() throws Exception {
        JoinPoint joinPoint = mock(JoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        
        Method method = this.getClass().getMethod("dummyUpdateMethod", Long.class, String.class);
        when(signature.getMethod()).thenReturn(method);
        when(joinPoint.getSignature()).thenReturn(signature);
        
        when(signature.getParameterNames()).thenReturn(new String[]{"id", "status"});
        Object[] args = new Object[]{ 10L, "DONE" };
        when(joinPoint.getArgs()).thenReturn(args);

        Auditable auditable = mock(Auditable.class);
        when(auditable.action()).thenReturn("APPOINTMENT_STATUS_CHANGED");
        when(auditable.entityType()).thenReturn("Appointment");

        auditAspect.logSuccessfulAction(joinPoint, auditable, null);

        verify(auditLogService).logAction(
                any(), any(), eq("APPOINTMENT_COMPLETED"), eq("Appointment"), eq(10L), any(), eq("SUCCESS")
        );
    }

    @Test
    void testStatusOverrideToCompletedWithoutParamNames() throws Exception {
        JoinPoint joinPoint = mock(JoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        
        Method method = this.getClass().getMethod("dummyUpdateMethod", Long.class, String.class);
        when(signature.getMethod()).thenReturn(method);
        when(joinPoint.getSignature()).thenReturn(signature);
        
        when(signature.getParameterNames()).thenReturn(null); // Force fallback to arg check
        Object[] args = new Object[]{ 10L, "DONE" };
        when(joinPoint.getArgs()).thenReturn(args);

        Auditable auditable = mock(Auditable.class);
        when(auditable.action()).thenReturn("APPOINTMENT_STATUS_CHANGED");
        when(auditable.entityType()).thenReturn("Appointment");

        auditAspect.logSuccessfulAction(joinPoint, auditable, null);

        verify(auditLogService).logAction(
                any(), any(), eq("APPOINTMENT_COMPLETED"), eq("Appointment"), eq(10L), any(), eq("SUCCESS")
        );
    }

    @Test
    void testParameterMasking() throws Exception {
        JoinPoint joinPoint = mock(JoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        
        Method method = this.getClass().getMethod("dummyCreateMethod");
        when(signature.getMethod()).thenReturn(method);
        when(joinPoint.getSignature()).thenReturn(signature);
        
        class SensitiveDto {
            public String password = "mySecretPassword";
            public String clientNotes = "some private client notes";
            public String name = "Public Name";
        }
        
        Object[] args = new Object[]{ new SensitiveDto() };
        when(joinPoint.getArgs()).thenReturn(args);

        Auditable auditable = mock(Auditable.class);
        when(auditable.action()).thenReturn("CREATE");
        when(auditable.entityType()).thenReturn("Dummy");
        when(auditable.captureArgs()).thenReturn(true);

        auditAspect.logSuccessfulAction(joinPoint, auditable, null);

        verify(auditLogService).logAction(
                any(), any(), any(), any(), any(),
                argThat(details -> details != null && details.contains("\"password\":\"***\"") && details.contains("\"clientNotes\":\"***\"") && details.contains("\"name\":\"Public Name\"")),
                eq("SUCCESS")
        );
    }

    @Test
    void testLogFailedAction_shouldAuditFailureWithExceptionMessage() {
        JoinPoint joinPoint = mock(JoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getArgs()).thenReturn(new Object[0]);

        Auditable auditable = mock(Auditable.class);
        when(auditable.action()).thenReturn("DELETE");
        when(auditable.entityType()).thenReturn("Dummy");

        Exception ex = new RuntimeException("Database error");

        auditAspect.logFailedAction(joinPoint, auditable, ex);

        verify(auditLogService).logAction(
                any(), any(), eq("DELETE"), eq("Dummy"), isNull(), isNull(), eq("FAILURE"), eq("Database error")
        );
    }

    @Test
    void testExtractEntityIdFromLongArgumentFallback() throws Exception {
        JoinPoint joinPoint = mock(JoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        
        Method method = this.getClass().getMethod("dummyCreateMethod");
        when(signature.getMethod()).thenReturn(method);
        when(joinPoint.getSignature()).thenReturn(signature);
        
        Object[] args = new Object[]{ "NotALong", 88L };
        when(joinPoint.getArgs()).thenReturn(args);

        Auditable auditable = mock(Auditable.class);
        when(auditable.action()).thenReturn("CREATE");
        when(auditable.entityType()).thenReturn("Dummy");

        auditAspect.logSuccessfulAction(joinPoint, auditable, null);

        verify(auditLogService).logAction(
                any(), any(), eq("CREATE"), eq("Dummy"), eq(88L), any(), eq("SUCCESS")
        );
    }

    @Test
    void testExtractEntityIdFromReflectionGetId() throws Exception {
        JoinPoint joinPoint = mock(JoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        Method method = this.getClass().getMethod("dummyCreateMethod");
        when(signature.getMethod()).thenReturn(method);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getArgs()).thenReturn(new Object[0]);

        class EntityWithGetId {
            public Long getId() {
                return 15L;
            }
        }

        Auditable auditable = mock(Auditable.class);
        when(auditable.action()).thenReturn("CREATE");
        when(auditable.entityType()).thenReturn("Dummy");

        auditAspect.logSuccessfulAction(joinPoint, auditable, new EntityWithGetId());

        verify(auditLogService).logAction(
                any(), any(), eq("CREATE"), eq("Dummy"), eq(15L), any(), eq("SUCCESS")
        );
    }

    @Test
    void testExtractEntityIdFromReflectionFieldId() throws Exception {
        JoinPoint joinPoint = mock(JoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        Method method = this.getClass().getMethod("dummyCreateMethod");
        when(signature.getMethod()).thenReturn(method);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getArgs()).thenReturn(new Object[0]);

        class EntityWithFieldId {
            private Long id = 25L;
        }

        Auditable auditable = mock(Auditable.class);
        when(auditable.action()).thenReturn("CREATE");
        when(auditable.entityType()).thenReturn("Dummy");

        auditAspect.logSuccessfulAction(joinPoint, auditable, new EntityWithFieldId());

        verify(auditLogService).logAction(
                any(), any(), eq("CREATE"), eq("Dummy"), eq(25L), any(), eq("SUCCESS")
        );
    }

    @Test
    void testMaskingNestedCollectionsAndMaps() throws Exception {
        JoinPoint joinPoint = mock(JoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        Method method = this.getClass().getMethod("dummyCreateMethod");
        when(signature.getMethod()).thenReturn(method);
        when(joinPoint.getSignature()).thenReturn(signature);

        Map<String, Object> innerMap = Map.of("password", "secret1", "name", "child");
        Map<String, Object> payload = Map.of(
                "token", "jwtSecret",
                "nestedList", List.of(innerMap),
                "nestedMap", Map.of("cvv", "123", "card", "4321")
        );

        Object[] args = new Object[]{ payload };
        when(joinPoint.getArgs()).thenReturn(args);

        Auditable auditable = mock(Auditable.class);
        when(auditable.action()).thenReturn("CREATE");
        when(auditable.entityType()).thenReturn("Dummy");
        when(auditable.captureArgs()).thenReturn(true);

        auditAspect.logSuccessfulAction(joinPoint, auditable, null);

        verify(auditLogService).logAction(
                any(), any(), any(), any(), any(),
                argThat(details -> details != null 
                        && details.contains("\"password\":\"***\"") 
                        && details.contains("\"token\":\"***\"")
                        && details.contains("\"cvv\":\"***\"")
                        && details.contains("\"card\":\"***\"")
                ),
                eq("SUCCESS")
        );
    }

    @Test
    void testMaskingFailureGracefulFallback() throws Exception {
        JoinPoint joinPoint = mock(JoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        Method method = this.getClass().getMethod("dummyCreateMethod");
        when(signature.getMethod()).thenReturn(method);
        when(joinPoint.getSignature()).thenReturn(signature);

        // A class that cannot be mapped easily or throws exception during conversion
        class UnmappableClass {
            @Override
            public String toString() { return "unmappable"; }
        }

        Object[] args = new Object[]{ new UnmappableClass() };
        when(joinPoint.getArgs()).thenReturn(args);

        Auditable auditable = mock(Auditable.class);
        when(auditable.action()).thenReturn("CREATE");
        when(auditable.entityType()).thenReturn("Dummy");
        when(auditable.captureArgs()).thenReturn(true);

        auditAspect.logSuccessfulAction(joinPoint, auditable, null);

        verify(auditLogService).logAction(
                any(), any(), any(), any(), any(),
                any(), eq("SUCCESS")
        );
    }

    @Test
    void testMaskingFailure_neverLeaksRawSensitiveFieldsInTheFallback() throws Exception {
        // Regressão: se objectMapper.convertValue() falhar por qualquer motivo (ex.: um getter
        // que lança), o fallback tinha um bug que devolvia o objeto CRU sem máscara — o que
        // vazaria password/CPF/chave PIX em texto puro no log. O fallback correto é uma string
        // segura que não contém nenhum dos campos sensíveis do objeto original.
        JoinPoint joinPoint = mock(JoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        Method method = this.getClass().getMethod("dummyCreateMethod");
        when(signature.getMethod()).thenReturn(method);
        when(joinPoint.getSignature()).thenReturn(signature);

        class ThrowsDuringConversion {
            public String password = "mySecretPassword123";

            public String getBroken() {
                throw new RuntimeException("boom");
            }
        }

        Object[] args = new Object[]{ new ThrowsDuringConversion() };
        when(joinPoint.getArgs()).thenReturn(args);

        Auditable auditable = mock(Auditable.class);
        when(auditable.action()).thenReturn("CREATE");
        when(auditable.entityType()).thenReturn("Dummy");
        when(auditable.captureArgs()).thenReturn(true);

        auditAspect.logSuccessfulAction(joinPoint, auditable, null);

        verify(auditLogService).logAction(
                any(), any(), any(), any(), any(),
                argThat(details -> details != null && !details.contains("mySecretPassword123")),
                eq("SUCCESS")
        );
    }

    @Test
    void testLogSuccessfulAction_whenSecurityContextHasNoAuth_shouldUseDefaultSystemEmail() throws Exception {
        SecurityContextHolder.clearContext();

        JoinPoint joinPoint = mock(JoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        Method method = this.getClass().getMethod("dummyCreateMethod");
        when(signature.getMethod()).thenReturn(method);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getArgs()).thenReturn(new Object[0]);

        Auditable auditable = mock(Auditable.class);
        when(auditable.action()).thenReturn("CREATE");
        when(auditable.entityType()).thenReturn("Dummy");

        auditAspect.logSuccessfulAction(joinPoint, auditable, null);

        verify(auditLogService).logAction(
                isNull(), eq("SYSTEM"), eq("CREATE"), eq("Dummy"), isNull(), isNull(), eq("SUCCESS")
        );
    }

    @Test
    void testLogSuccessfulAction_whenSecurityContextHasNonUserDetailsPrincipal_shouldReturnNullUserId() throws Exception {
        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn("anonymousUser");
        when(auth.getName()).thenReturn("anonymous@email.com");
        
        SecurityContext context = mock(SecurityContext.class);
        when(context.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(context);

        JoinPoint joinPoint = mock(JoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        Method method = this.getClass().getMethod("dummyCreateMethod");
        when(signature.getMethod()).thenReturn(method);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getArgs()).thenReturn(new Object[0]);

        Auditable auditable = mock(Auditable.class);
        when(auditable.action()).thenReturn("CREATE");
        when(auditable.entityType()).thenReturn("Dummy");

        auditAspect.logSuccessfulAction(joinPoint, auditable, null);

        verify(auditLogService).logAction(
                isNull(), eq("anonymous@email.com"), eq("CREATE"), eq("Dummy"), isNull(), isNull(), eq("SUCCESS")
        );
        SecurityContextHolder.clearContext();
    }

    @Test
    void testLogSuccessfulAction_whenSecurityContextHasValidUserPrincipal_shouldReturnUserIdAndEmail() throws Exception {
        User user = mock(User.class);
        when(user.getId()).thenReturn(500L);
        
        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(user);
        when(auth.getName()).thenReturn("user@email.com");
        
        SecurityContext context = mock(SecurityContext.class);
        when(context.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(context);

        JoinPoint joinPoint = mock(JoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        Method method = this.getClass().getMethod("dummyCreateMethod");
        when(signature.getMethod()).thenReturn(method);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getArgs()).thenReturn(new Object[0]);

        Auditable auditable = mock(Auditable.class);
        when(auditable.action()).thenReturn("CREATE");
        when(auditable.entityType()).thenReturn("Dummy");

        auditAspect.logSuccessfulAction(joinPoint, auditable, null);

        verify(auditLogService).logAction(
                eq(500L), eq("user@email.com"), eq("CREATE"), eq("Dummy"), isNull(), isNull(), eq("SUCCESS")
        );
        SecurityContextHolder.clearContext();
    }

    @Test
    void testStatusOverrideToCompletedWithNullArgs() throws Exception {
        JoinPoint joinPoint = mock(JoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        Method method = this.getClass().getMethod("dummyCreateMethod");
        when(signature.getMethod()).thenReturn(method);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getArgs()).thenReturn(null); // Null args

        Auditable auditable = mock(Auditable.class);
        when(auditable.action()).thenReturn("APPOINTMENT_STATUS_CHANGED");
        when(auditable.entityType()).thenReturn("Appointment");

        auditAspect.logSuccessfulAction(joinPoint, auditable, null);

        verify(auditLogService).logAction(
                any(), any(), eq("APPOINTMENT_STATUS_CHANGED"), eq("Appointment"), isNull(), isNull(), eq("SUCCESS")
        );
    }
}
