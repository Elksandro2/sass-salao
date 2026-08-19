package com.cristiane.salon.models.appointment.controller;

import com.cristiane.salon.controllers.BaseControllerTest;

import com.cristiane.salon.models.appointment.controller.AppointmentController;
import com.cristiane.salon.models.appointment.dto.AppointmentResponse;
import com.cristiane.salon.models.appointment.dto.AppointmentServiceResponse;
import com.cristiane.salon.models.appointment.service.AppointmentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AppointmentController.class)
class AppointmentControllerTest extends BaseControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AppointmentService appointmentService;

    @Test
    @WithMockUser
    void createReturns201_whenValid() throws Exception {
        when(appointmentService.create(any())).thenReturn(null);

        String body = "{\"employeeId\":1,\"services\":[{\"serviceId\":1}]}";

        mvc.perform(post("/v1/appointments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser
    void createReturns400_whenInvalid() throws Exception {
        String body = "{}";

        mvc.perform(post("/v1/appointments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    void confirmReturns200() throws Exception {
        AppointmentResponse response = new AppointmentResponse(
                1L, 1L, "Client", 2L, "Employee",
                List.of(new AppointmentServiceResponse(3L, "Service", null, null, null, null)),
                List.of(), List.of(),
                null, null, null, null,
                LocalDateTime.now(), LocalDate.now(), "Notes", null, "CONFIRMED", null, null, null, null, false, ""
        );
        when(appointmentService.confirm(eq(1L), any())).thenReturn(response);

        String body = "{\"scheduledAt\":\"2026-06-16T10:00:00\"}";

        mvc.perform(patch("/v1/appointments/1/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    @WithMockUser
    void declineReturns200() throws Exception {
        AppointmentResponse response = new AppointmentResponse(
                1L, 1L, "Client", 2L, "Employee",
                List.of(new AppointmentServiceResponse(3L, "Service", null, null, null, null)),
                List.of(), List.of(),
                null, null, null, null,
                LocalDateTime.now(), LocalDate.now(), "Notes", null, "DECLINED", null, null, null, null, false, ""
        );
        when(appointmentService.decline(eq(1L))).thenReturn(response);

        mvc.perform(patch("/v1/appointments/1/decline")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DECLINED"));
    }

    @Test
    @WithMockUser
    void getMyAppointmentsReturnsList() throws Exception {
        AppointmentResponse response = new AppointmentResponse(
                1L, 1L, "Client", 2L, "Employee",
                List.of(new AppointmentServiceResponse(3L, "Service", null, null, null, null)),
                List.of(), List.of(),
                null, null, null, null,
                LocalDateTime.now(), LocalDate.now(), "Notes", null, "REQUESTED", null, null, null, null, false, ""
        );
        when(appointmentService.getMyAppointments()).thenReturn(List.of(response));

        mvc.perform(get("/v1/appointments/my")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].clientName").value("Client"));
    }

    @Test
    @WithMockUser
    void findAllReturnsPageOfAppointments() throws Exception {
        AppointmentResponse response = new AppointmentResponse(
                1L, 1L, "Client", 2L, "Employee",
                List.of(new AppointmentServiceResponse(3L, "Service", null, null, null, null)),
                List.of(), List.of(),
                null, null, null, null,
                LocalDateTime.now(), LocalDate.now(), "Notes", null, "CONFIRMED", null, null, null, null, false, ""
        );
        org.springframework.data.domain.Page<AppointmentResponse> page =
                new org.springframework.data.domain.PageImpl<>(List.of(response));
        when(appointmentService.findAll(any(), any())).thenReturn(page);

        mvc.perform(get("/v1/appointments")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].clientName").value("Client"));
    }

    @Test
    @WithMockUser
    void cancelReturns200() throws Exception {
        AppointmentResponse response = new AppointmentResponse(
                1L, 1L, "Client", 2L, "Employee",
                List.of(new AppointmentServiceResponse(3L, "Service", null, null, null, null)),
                List.of(), List.of(),
                null, null, null, null,
                LocalDateTime.now(), LocalDate.now(), "Notes", null, "CANCELLED", null, null, null, null, false, ""
        );
        when(appointmentService.cancel(eq(1L))).thenReturn(response);

        mvc.perform(patch("/v1/appointments/1/cancel")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    @WithMockUser
    void updateStatusReturns200() throws Exception {
        AppointmentResponse response = new AppointmentResponse(
                1L, 1L, "Client", 2L, "Employee",
                List.of(new AppointmentServiceResponse(3L, "Service", null, null, null, null)),
                List.of(), List.of(),
                null, null, null, null,
                LocalDateTime.now(), LocalDate.now(), "Notes", null, "DONE", null, null, null, null, false, ""
        );
        when(appointmentService.updateStatus(eq(1L), eq("DONE"))).thenReturn(response);

        mvc.perform(patch("/v1/appointments/1/status")
                .param("status", "DONE")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DONE"));
     }

    @Test
    @WithMockUser
    void updatePaymentStatusReturns200() throws Exception {
        AppointmentResponse response = new AppointmentResponse(
                1L, 1L, "Client", 2L, "Employee",
                List.of(new AppointmentServiceResponse(3L, "Service", null, null, null, null)),
                List.of(), List.of(),
                null, null, null, null,
                LocalDateTime.now(), LocalDate.now(), "Notes", null, "CONFIRMED", "PAID", null, null, null, false, ""
        );
        when(appointmentService.updatePaymentStatus(eq(1L), eq("PAID"), eq(null))).thenReturn(response);

        mvc.perform(patch("/v1/appointments/1/payment-status")
                .param("paymentStatus", "PAID")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentStatus").value("PAID"));
    }

    @Test
    @WithMockUser
    void findByIdReturns200() throws Exception {
        AppointmentResponse response = new AppointmentResponse(
                1L, 1L, "Client", 2L, "Employee",
                List.of(new AppointmentServiceResponse(3L, "Service", null, null, null, null)),
                List.of(), List.of(),
                null, null, null, null,
                LocalDateTime.now(), LocalDate.now(), "Notes", null, "CONFIRMED", null, null, null, null, false, ""
        );
        when(appointmentService.findById(1L)).thenReturn(response);

        mvc.perform(get("/v1/appointments/1")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientName").value("Client"));
    }
}
