package com.cristiane.salon.models.cashflow.controller;

import com.cristiane.salon.controllers.BaseControllerTest;

import com.cristiane.salon.models.cashflow.controller.CashFlowController;
import com.cristiane.salon.models.cashflow.dto.CashFlowResponse;
import com.cristiane.salon.models.cashflow.service.CashFlowService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CashFlowController.class)
class CashFlowControllerTest extends BaseControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private CashFlowService cashFlowService;

    @Test
    @WithMockUser
    void createReturns201_whenValid() throws Exception {
        when(cashFlowService.create(any())).thenReturn(null);

        String body = "{\"type\":\"INCOME\",\"amount\":100.0,\"description\":\"t\",\"date\":\"2026-05-16\"}";

        mvc.perform(post("/v1/cashflow")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser
    void createReturns400_whenInvalid() throws Exception {
        String body = "{}";

        mvc.perform(post("/v1/cashflow")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    void findByPeriodReturns200() throws Exception {
        CashFlowResponse response = new CashFlowResponse(1L, "INCOME", new BigDecimal("100.0"), "Desc", LocalDate.now(), null, null, null, null);
        org.springframework.data.domain.Page<CashFlowResponse> page =
                new org.springframework.data.domain.PageImpl<>(List.of(response));
        when(cashFlowService.findByPeriod(any(), any(), any())).thenReturn(page);

        mvc.perform(get("/v1/cashflow")
                .param("from", "2026-06-16")
                .param("to", "2026-06-16")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].description").value("Desc"));
    }

    @Test
    @WithMockUser
    void deleteReturns204() throws Exception {
        doNothing().when(cashFlowService).delete(eq(1L));

        mvc.perform(delete("/v1/cashflow/1")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent());
    }
}
