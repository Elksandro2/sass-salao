package com.cristiane.salon.models.service.controller;

import com.cristiane.salon.controllers.BaseControllerTest;

import com.cristiane.salon.models.service.controller.SalonServiceController;
import com.cristiane.salon.models.service.service.SalonServiceManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import com.cristiane.salon.models.service.dto.SalonServiceResponse;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SalonServiceController.class)
class SalonServiceControllerTest extends BaseControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private SalonServiceManager salonServiceManager;

    @Test
    @WithMockUser
    void createReturns201_whenValid() throws Exception {
        when(salonServiceManager.create(any())).thenReturn(null);

        String body = "{\"name\":\"cut\"}";

        mvc.perform(post("/v1/services")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser
    void createReturns400_whenInvalid() throws Exception {
        String body = "{}";

        mvc.perform(post("/v1/services")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    void reactivateReturns200() throws Exception {
        SalonServiceResponse dummyResponse = new SalonServiceResponse(
                1L, "cut", "description", new BigDecimal("50.0"), true, java.util.List.of(), null
        );
        when(salonServiceManager.reactivate(any())).thenReturn(dummyResponse);

        mvc.perform(patch("/v1/services/1/reactivate"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    void findAllReturnsPageOfServices() throws Exception {
        SalonServiceResponse response = new SalonServiceResponse(
                1L, "Haircut", "Classic trim", new BigDecimal("45.00"), true, java.util.List.of(), null
        );
        org.springframework.data.domain.Page<SalonServiceResponse> page =
                new org.springframework.data.domain.PageImpl<>(List.of(response));
        when(salonServiceManager.findAll(any(), any())).thenReturn(page);

        mvc.perform(get("/v1/services")
                .param("active", "true")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Haircut"));
    }

    @Test
    @WithMockUser
    void findByIdReturnsService() throws Exception {
        SalonServiceResponse response = new SalonServiceResponse(
                2L, "Manicure", "Nail care", new BigDecimal("30.00"), true, java.util.List.of(), null
        );
        when(salonServiceManager.findById(eq(2L))).thenReturn(response);

        mvc.perform(get("/v1/services/2")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Manicure"));
    }

    @Test
    @WithMockUser
    void updateReturnsUpdatedService() throws Exception {
        SalonServiceResponse response = new SalonServiceResponse(
                2L, "Pedicure", "Nail care", new BigDecimal("35.00"), true, java.util.List.of(), null
        );
        when(salonServiceManager.update(eq(2L), any())).thenReturn(response);

        String body = "{\"name\":\"Pedicure\"}";

        mvc.perform(put("/v1/services/2")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Pedicure"));
    }

    @Test
    @WithMockUser
    void deleteReturnsNoContent() throws Exception {
        doNothing().when(salonServiceManager).delete(eq(3L));

        mvc.perform(delete("/v1/services/3")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent());
    }
}
