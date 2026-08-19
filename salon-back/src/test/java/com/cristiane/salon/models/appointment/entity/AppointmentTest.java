package com.cristiane.salon.models.appointment.entity;

import com.cristiane.salon.models.appointment.enums.ExpenseValueType;
import com.cristiane.salon.models.product.entity.Product;
import com.cristiane.salon.models.service.entity.SalonService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AppointmentTest {

    private Appointment appointment;
    private SalonService haircut;
    private SalonService coloring;
    private AppointmentServiceItem haircutItem;
    private AppointmentServiceItem coloringItem;

    @BeforeEach
    void setUp() {
        haircut = new SalonService();
        haircut.setName("Corte");
        haircut.setPrice(new BigDecimal("100.00"));

        coloring = new SalonService();
        coloring.setName("Coloração");
        coloring.setPrice(new BigDecimal("150.00"));

        appointment = new Appointment();

        haircutItem = new AppointmentServiceItem();
        haircutItem.setAppointment(appointment);
        haircutItem.setSalonService(haircut);

        coloringItem = new AppointmentServiceItem();
        coloringItem.setAppointment(appointment);
        coloringItem.setSalonService(coloring);
    }

    @Test
    void getEffectivePrice_whenNoCustomPrice_shouldReturnCatalogPrice() {
        assertThat(haircutItem.getEffectivePrice()).isEqualByComparingTo("100.00");
    }

    @Test
    void getEffectivePrice_whenCustomPriceSet_shouldReturnCustomPriceAndLeaveCatalogUntouched() {
        haircutItem.setCustomPrice(new BigDecimal("200.00"));

        assertThat(haircutItem.getEffectivePrice()).isEqualByComparingTo("200.00");
        assertThat(haircut.getPrice()).isEqualByComparingTo("100.00");
    }

    @Test
    void getTotalEffectivePrice_withMultipleServices_shouldSumEffectivePrices() {
        haircutItem.setCustomPrice(new BigDecimal("120.00"));
        appointment.setServices(List.of(haircutItem, coloringItem));

        assertThat(appointment.getTotalEffectivePrice()).isEqualByComparingTo("270.00");
    }

    @Test
    void getServiceNames_withMultipleServices_shouldJoinNamesWithComma() {
        appointment.setServices(List.of(haircutItem, coloringItem));

        assertThat(appointment.getServiceNames()).isEqualTo("Corte, Coloração");
    }

    private Product shampoo() {
        Product product = new Product();
        product.setName("Shampoo");
        product.setPrice(new BigDecimal("50.00"));
        product.setStock(10);
        product.setActive(true);
        return product;
    }

    private AppointmentProductItem productItem(Product product, int quantity, BigDecimal customPrice) {
        AppointmentProductItem item = new AppointmentProductItem();
        item.setAppointment(appointment);
        item.setProduct(product);
        item.setQuantity(quantity);
        item.setCustomPrice(customPrice);
        return item;
    }

    private AppointmentExpenseItem expenseItem(String description, ExpenseValueType type, BigDecimal value) {
        AppointmentExpenseItem item = new AppointmentExpenseItem();
        item.setAppointment(appointment);
        item.setDescription(description);
        item.setValueType(type);
        item.setValue(value);
        return item;
    }

    @Test
    void getTotalProductsPrice_withMultipleProducts_shouldSumQuantityTimesEffectivePrice() {
        appointment.setProducts(List.of(
                productItem(shampoo(), 2, null),
                productItem(shampoo(), 1, new BigDecimal("30.00"))
        ));

        assertThat(appointment.getTotalProductsPrice()).isEqualByComparingTo("130.00");
    }

    @Test
    void getExpenseBaseAmount_shouldSumServicesAndProducts() {
        appointment.setServices(List.of(haircutItem));
        appointment.setProducts(List.of(productItem(shampoo(), 1, null)));

        assertThat(appointment.getExpenseBaseAmount()).isEqualByComparingTo("150.00");
    }

    @Test
    void getTotalExpensesAmount_withFixedExpense_shouldReturnFixedValue() {
        appointment.setServices(List.of(haircutItem));
        appointment.setExpenses(List.of(expenseItem("Material", ExpenseValueType.FIXED, new BigDecimal("20.00"))));

        assertThat(appointment.getTotalExpensesAmount()).isEqualByComparingTo("20.00");
    }

    @Test
    void getTotalExpensesAmount_withPercentageExpense_shouldCalculateOverServicesPlusProducts() {
        appointment.setServices(List.of(haircutItem));
        appointment.setProducts(List.of(productItem(shampoo(), 1, null)));
        appointment.setExpenses(List.of(expenseItem("Taxa", ExpenseValueType.PERCENTAGE, new BigDecimal("10"))));

        // base = 100 (serviço) + 50 (produto) = 150; 10% = 15
        assertThat(appointment.getTotalExpensesAmount()).isEqualByComparingTo("15.00");
    }

    @Test
    void getGrandTotal_withServicesProductsAndExpenses_shouldSubtractExpensesFromTotal() {
        appointment.setServices(List.of(haircutItem));
        appointment.setProducts(List.of(productItem(shampoo(), 1, null)));
        appointment.setExpenses(List.of(expenseItem("Material", ExpenseValueType.FIXED, new BigDecimal("30.00"))));

        // 100 (serviço) + 50 (produto) - 30 (despesa) = 120
        assertThat(appointment.getGrandTotal()).isEqualByComparingTo("120.00");
    }

    @Test
    void getGrandTotal_whenExpensesExceedTotal_shouldFloorAtZero() {
        appointment.setServices(List.of(haircutItem));
        appointment.setExpenses(List.of(expenseItem("Material", ExpenseValueType.FIXED, new BigDecimal("500.00"))));

        assertThat(appointment.getGrandTotal()).isEqualByComparingTo("0");
    }

    @Test
    void getGrandTotal_withNoProductsOrExpenses_shouldEqualTotalEffectivePrice() {
        appointment.setServices(List.of(haircutItem, coloringItem));

        assertThat(appointment.getGrandTotal()).isEqualByComparingTo(appointment.getTotalEffectivePrice());
    }
}
