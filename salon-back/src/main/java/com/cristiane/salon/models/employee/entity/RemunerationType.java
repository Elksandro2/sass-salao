package com.cristiane.salon.models.employee.entity;

public enum RemunerationType {
    SALARIO_FIXO,
    COMISSIONADO,
    FIXO_E_COMISSIONADO,
    /** Recebe por dia trabalhado: {@code remunerationValue} é o valor da diária. Sem comissão. */
    DIARISTA,
    /** Diária ({@code remunerationValue}) + comissão por serviço/produto, como no Fixo+Comissionado. */
    DIARIA_E_COMISSIONADO;

    /** Recebe comissão de serviço (o % de cada SalonService realizado). */
    public boolean paysServiceCommission() {
        return this == COMISSIONADO || this == FIXO_E_COMISSIONADO || this == DIARIA_E_COMISSIONADO;
    }

    /** Tem salário fixo mensal como base ({@code remunerationValue}). */
    public boolean hasFixedSalary() {
        return this == SALARIO_FIXO || this == FIXO_E_COMISSIONADO;
    }

    /** Recebe por dia trabalhado: {@code remunerationValue} é a diária, paga × dias no período. */
    public boolean isDaily() {
        return this == DIARISTA || this == DIARIA_E_COMISSIONADO;
    }

    /** Precisa de {@code remunerationValue} preenchido (salário base OU valor da diária). */
    public boolean requiresValue() {
        return hasFixedSalary() || isDaily();
    }
}
