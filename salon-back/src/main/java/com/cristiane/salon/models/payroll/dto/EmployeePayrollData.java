package com.cristiane.salon.models.payroll.dto;

import com.cristiane.salon.models.appointment.entity.Appointment;
import com.cristiane.salon.models.cashflow.entity.CashFlow;
import com.cristiane.salon.models.employee.entity.Employee;
import com.cristiane.salon.models.payroll.entity.PayrollAdjustment;

import java.time.LocalDate;
import java.util.List;

/**
 * Tudo que compõe a folha de uma colaboradora num período, já carregado e calculado.
 *
 * @param workDays                  diárias que contam (automáticas + registradas), por data
 * @param unmarkedAppointmentDates  dias com atendimento que foram desmarcados de propósito
 */
public record EmployeePayrollData(
        Employee employee,
        boolean active,
        List<Appointment> doneAppointments,
        List<CashFlow> productSales,
        List<WorkDayEntry> workDays,
        List<LocalDate> unmarkedAppointmentDates,
        List<PayrollAdjustment> adjustments,
        PayrollBreakdown breakdown
) {}
