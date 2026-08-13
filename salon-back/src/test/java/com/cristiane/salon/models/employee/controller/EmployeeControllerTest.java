package com.cristiane.salon.models.employee.controller;

import com.cristiane.salon.controllers.BaseControllerTest;

import com.cristiane.salon.models.employee.controller.EmployeeController;
import com.cristiane.salon.models.employee.dto.EmployeeBookingResponse;
import com.cristiane.salon.models.employee.dto.EmployeeResponse;
import com.cristiane.salon.models.employee.entity.CommissionScope;
import com.cristiane.salon.models.employee.entity.RemunerationType;
import com.cristiane.salon.models.employee.service.EmployeeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EmployeeController.class)
class EmployeeControllerTest extends BaseControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private EmployeeService employeeService;

    @Test
    @WithMockUser(roles = { "ADMIN" })
    void createReturns201_whenValid() throws Exception {
        when(employeeService.create(any())).thenReturn(null);

        String body = "{\"userId\":1}";

        mvc.perform(post("/v1/employees")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(roles = { "ADMIN" })
    void createReturns400_whenInvalid() throws Exception {
        String body = "{}";

        mvc.perform(post("/v1/employees")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    void findAllReturnsPageOfEmployees() throws Exception {
        EmployeeResponse response = new EmployeeResponse(
                1L, 1L, "Alice", "alice@example.com", "FUNCIONARIA",
                RemunerationType.COMISSIONADO, CommissionScope.INDIVIDUAL,
                BigDecimal.ZERO, BigDecimal.TEN, null
        );
        org.springframework.data.domain.Page<EmployeeResponse> page =
                new org.springframework.data.domain.PageImpl<>(List.of(response));
        when(employeeService.findAll(any(), any())).thenReturn(page);

        mvc.perform(get("/v1/employees")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Alice"));
    }

    @Test
    @WithMockUser
    void findAllForBookingReturnsEmployees() throws Exception {
        EmployeeBookingResponse response = new EmployeeBookingResponse(1L, 1L, "Bob");
        when(employeeService.findAllForBooking()).thenReturn(List.of(response));

        mvc.perform(get("/v1/employees/booking")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Bob"));
    }

    @Test
    @WithMockUser(roles = { "ADMIN" })
    void updateReturnsUpdatedEmployee() throws Exception {
        EmployeeResponse response = new EmployeeResponse(
                2L, 1L, "Alice", "alice@example.com", "FUNCIONARIA",
                RemunerationType.SALARIO_FIXO, null,
                BigDecimal.TEN, BigDecimal.ZERO, null
        );
        when(employeeService.update(eq(2L), any())).thenReturn(response);

        String body = "{\"userId\":1,\"remunerationType\":\"SALARIO_FIXO\",\"remunerationValue\":10.0}";

        mvc.perform(put("/v1/employees/2")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.remunerationType").value("SALARIO_FIXO"));
    }

    @Test
    @WithMockUser(roles = { "ADMIN" })
    void deleteReturnsNoContent() throws Exception {
        doNothing().when(employeeService).delete(eq(3L));

        mvc.perform(delete("/v1/employees/3")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent());
    }
}
