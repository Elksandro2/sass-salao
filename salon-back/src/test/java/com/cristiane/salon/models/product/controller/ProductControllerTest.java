package com.cristiane.salon.models.product.controller;

import com.cristiane.salon.controllers.BaseControllerTest;

import com.cristiane.salon.models.product.controller.ProductController;
import com.cristiane.salon.models.product.service.ProductService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import com.cristiane.salon.models.product.dto.ProductResponse;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductController.class)
class ProductControllerTest extends BaseControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ProductService productService;

    @Test
    @WithMockUser
    void createReturns201_whenValid() throws Exception {
        when(productService.create(any())).thenReturn(null);

        String body = "{\"name\":\"xyz\",\"price\":10.0}";

        mvc.perform(post("/v1/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser
    void createReturns400_whenInvalid() throws Exception {
        String body = "{}";

        mvc.perform(post("/v1/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    void reactivateReturns200() throws Exception {
        ProductResponse dummyResponse = new ProductResponse(1L, "XYZ", new BigDecimal("10.0"), true, null, null, null, null, null, null, null);
        when(productService.reactivate(any())).thenReturn(dummyResponse);

        mvc.perform(patch("/v1/products/1/reactivate"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    void findAllReturnsPageOfProducts() throws Exception {
        ProductResponse response = new ProductResponse(1L, "Hair Spray", new BigDecimal("15.50"), true, null, null, null, null, null, null, null);
        org.springframework.data.domain.Page<ProductResponse> page =
                new org.springframework.data.domain.PageImpl<>(List.of(response));
        when(productService.findAll(any(), any())).thenReturn(page);

        mvc.perform(get("/v1/products")
                .param("active", "true")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Hair Spray"));
    }

    @Test
    @WithMockUser
    void findByIdReturnsProduct() throws Exception {
        ProductResponse response = new ProductResponse(2L, "Shampoo", new BigDecimal("25.00"), true, null, null, null, null, null, null, null);
        when(productService.findById(eq(2L))).thenReturn(response);

        mvc.perform(get("/v1/products/2")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Shampoo"));
    }

    @Test
    @WithMockUser
    void updateReturnsUpdatedProduct() throws Exception {
        ProductResponse response = new ProductResponse(2L, "New Shampoo", new BigDecimal("28.00"), true, null, null, null, null, null, null, null);
        when(productService.update(eq(2L), any())).thenReturn(response);

        String body = "{\"name\":\"New Shampoo\",\"price\":28.00}";

        mvc.perform(put("/v1/products/2")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("New Shampoo"));
    }

    @Test
    @WithMockUser
    void deleteReturnsNoContent() throws Exception {
        doNothing().when(productService).delete(eq(3L));

        mvc.perform(delete("/v1/products/3")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent());
    }
}
