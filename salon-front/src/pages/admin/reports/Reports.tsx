import { useState, useEffect } from 'react';
import { reportsApi } from './services/reports';
import type {
  FinancialReportResponse,
  AppointmentReportResponse,
  PayrollReportResponse,
  ServicePricingAnalysisResponse,
} from './services/reports';
import { EmployeeFinancialHistorySection } from './EmployeeFinancialHistorySection';
import { cashFlowApi } from '../cashflow/services/cashflow';
import type { CashFlowData } from '../cashflow/services/cashflow';
import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
  LineChart,
  Line,
  Legend,
} from 'recharts';
import jsPDF from 'jspdf';
import autoTable from 'jspdf-autotable';
import { Download, Loader2, TrendingUp, TrendingDown, AlertTriangle } from 'lucide-react';
import { useAlert } from '../../../hooks/useAlert';
import { getApiErrorMessage } from '../../../utils/apiError';
import { useAuth } from '../../../hooks/useAuth';

const inputCls = 'input-premium';
const labelCls = 'label-premium';

export const Reports = () => {
  const { user } = useAuth();
  const [financial, setFinancial] = useState<FinancialReportResponse | null>(null);
  const [appointments, setAppointments] = useState<AppointmentReportResponse | null>(null);
  const [cashFlows, setCashFlows] = useState<CashFlowData[]>([]);
  const [payroll, setPayroll] = useState<PayrollReportResponse | null>(null);
  const [servicePricing, setServicePricing] = useState<ServicePricingAnalysisResponse | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isGeneratingPDF, setIsGeneratingPDF] = useState(false);
  const [activeTab, setActiveTab] = useState<'financial' | 'payroll' | 'appointments' | 'pricing'>(
    'financial'
  );

  const [dateFrom, setDateFrom] = useState('');
  const [dateTo, setDateTo] = useState('');

  const { error: showError } = useAlert();

  const loadReports = async () => {
    setIsLoading(true);
    try {
      const [finData, aptData, cfData, payData, pricingData] = await Promise.all([
        reportsApi.getFinancialReport(dateFrom || undefined, dateTo || undefined),
        reportsApi.getAppointmentReport(dateFrom || undefined, dateTo || undefined),
        cashFlowApi.findByPeriod(dateFrom || undefined, dateTo || undefined, 0, 1000),
        reportsApi.getPayrollReport(dateFrom || undefined, dateTo || undefined),
        reportsApi.getServicePricingAnalysis(dateFrom || undefined, dateTo || undefined),
      ]);
      setFinancial(finData);
      setAppointments(aptData);
      setCashFlows(cfData.content);
      setPayroll(payData);
      setServicePricing(pricingData);
    } catch (err) {
      const msg = getApiErrorMessage(err, 'Erro ao carregar relatórios');
      await showError(msg);
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    loadReports();
  }, [dateFrom, dateTo]);

  const generatePDF = async () => {
    setIsGeneratingPDF(true);
    try {
      // Fetch fresh cash flow and payroll data before drawing the PDF (user requirement)
      const [cfPage, payData] = await Promise.all([
        cashFlowApi.findByPeriod(dateFrom || undefined, dateTo || undefined, 0, 1000),
        reportsApi.getPayrollReport(dateFrom || undefined, dateTo || undefined),
      ]);
      const cfData = cfPage.content;

      const doc = new jsPDF();
      const margin = 15;
      const docWidth = 210;

      // 1. Draw modern, elegant header banner matching the brand colors (Dusty Rose #be8a83)
      doc.setFillColor(190, 138, 131);
      doc.rect(0, 0, docWidth, 40, 'F');

      doc.setTextColor(255, 255, 255);
      doc.setFont('helvetica', 'bold');
      doc.setFontSize(20);
      doc.text('ESPAÇO CRISTIANE MOURA', margin, 18);

      doc.setFont('helvetica', 'normal');
      doc.setFontSize(10);
      doc.text('Relatório Executivo de Finanças e Agendamentos', margin, 26);

      const authorText = user?.email ? `  |  Gerado por: ${user.email}` : '';
      doc.text(`Período: ${financial?.period || 'Geral'}${authorText}`, margin, 32);

      let currentY = 48;

      // Helper function to draw consistent section headers
      const drawSectionHeader = (title: string, y: number) => {
        doc.setFont('helvetica', 'bold');
        doc.setFontSize(13);
        doc.setTextColor(59, 48, 54); // Charcoal #3b3036
        doc.text(title, margin, y);

        // Subtle divider line
        doc.setDrawColor(234, 225, 225); // #eae1e1
        doc.setLineWidth(0.5);
        doc.line(margin, y + 2, docWidth - margin, y + 2);
        return y + 8;
      };

      // 2. Financial Summary Table
      currentY = drawSectionHeader('1. Resumo Financeiro', currentY);
      autoTable(doc, {
        startY: currentY,
        head: [
          ['Receitas', 'Despesas Gerais', 'Gastos Salários', 'Gastos Comissões', 'Lucro Líquido'],
        ],
        body: [
          [
            `R$ ${(financial?.totalIncome ?? 0).toFixed(2)}`,
            `R$ ${(financial?.totalExpense ?? 0).toFixed(2)}`,
            `R$ ${(financial?.totalSalaryPaid ?? 0).toFixed(2)}`,
            `R$ ${(financial?.totalCommissionPaid ?? 0).toFixed(2)}`,
            `R$ ${(financial?.netProfit ?? 0).toFixed(2)}`,
          ],
        ],
        theme: 'striped',
        headStyles: { fillColor: [190, 138, 131], textColor: [255, 255, 255], fontStyle: 'bold' },
        bodyStyles: { textColor: [59, 48, 54] },
        alternateRowStyles: { fillColor: [252, 249, 249] },
        margin: { left: margin, right: margin },
      });

      currentY = (doc as any).lastAutoTable.finalY + 12;

      // 3. Employee Remunerations Table
      if (financial?.employeeFinanceDetails && financial.employeeFinanceDetails.length > 0) {
        if (currentY > 230) {
          doc.addPage();
          currentY = 20;
        }
        currentY = drawSectionHeader('2. Detalhamento de Remunerações por Funcionário(a)', currentY);

        const employeeRows = financial.employeeFinanceDetails.map((emp) => {
          let typeStr = 'Não definido';
          let baseStr = 'R$ 0,00';

          if (emp.remunerationType === 'SALARIO_FIXO') {
            typeStr = 'Salário Fixo';
            baseStr = `R$ ${(emp.remunerationValue ?? 0).toFixed(2)}`;
          } else if (emp.remunerationType === 'COMISSIONADO') {
            typeStr = 'Comissionado';
            const scopeStr = emp.commissionScope === 'GLOBAL' ? 'Global' : 'Individual';
            baseStr = `${emp.remunerationValue ?? 0}% (${scopeStr})`;
          } else if (emp.remunerationType === 'FIXO_E_COMISSIONADO') {
            typeStr = 'Fixo + Comissionado';
            const scopeStr = emp.commissionScope === 'GLOBAL' ? 'Global' : 'Individual';
            baseStr = `R$ ${(emp.remunerationValue ?? 0).toFixed(2)} + ${(emp.commissionValue ?? 0).toFixed(0)}% (${scopeStr})`;
          }

          return [
            emp.employeeName,
            typeStr,
            baseStr,
            emp.doneAppointmentsCount.toString(),
            `R$ ${(emp.doneAppointmentsValue ?? 0).toFixed(2)}`,
            `R$ ${(emp.calculatedPayout ?? 0).toFixed(2)}`,
          ];
        });

        autoTable(doc, {
          startY: currentY,
          head: [['Nome', 'Tipo', 'Base', 'Atendimentos', 'Valor Atendimentos', 'Valor A Pagar']],
          body: employeeRows,
          theme: 'striped',
          headStyles: { fillColor: [59, 48, 54], textColor: [255, 255, 255], fontStyle: 'bold' },
          bodyStyles: { textColor: [59, 48, 54] },
          alternateRowStyles: { fillColor: [252, 249, 249] },
          margin: { left: margin, right: margin },
        });

        currentY = (doc as any).lastAutoTable.finalY + 12;
      }

      // 4. Appointment Summary Table
      if (currentY > 230) {
        doc.addPage();
        currentY = 20;
      }
      currentY = drawSectionHeader('3. Resumo de Agendamentos', currentY);
      autoTable(doc, {
        startY: currentY,
        head: [['Total', 'Concluídos', 'Pendentes', 'Cancelados']],
        body: [
          [
            appointments?.totalAppointments || 0,
            appointments?.done || 0,
            appointments?.pending || 0,
            appointments?.cancelled || 0,
          ],
        ],
        theme: 'striped',
        headStyles: { fillColor: [190, 138, 131], textColor: [255, 255, 255], fontStyle: 'bold' },
        bodyStyles: { textColor: [59, 48, 54] },
        alternateRowStyles: { fillColor: [252, 249, 249] },
        margin: { left: margin, right: margin },
      });

      currentY = (doc as any).lastAutoTable.finalY + 12;

      // 5. Payroll Table inside PDF (aligned to scope requirements)
      if (payData && payData.items && payData.items.length > 0) {
        if (currentY > 200) {
          doc.addPage();
          currentY = 20;
        }
        currentY = drawSectionHeader('4. Folha de Pagamento', currentY);

        const payrollRows = payData.items.map((item) => {
          let typeStr = 'Não definido';
          if (item.remunerationType === 'SALARIO_FIXO') typeStr = 'Salário Fixo';
          else if (item.remunerationType === 'COMISSIONADO') typeStr = 'Comissionado';
          else if (item.remunerationType === 'FIXO_E_COMISSIONADO') typeStr = 'Misto';

          return [
            item.employeeName,
            typeStr,
            `R$ ${item.baseAmount.toFixed(2)}`,
            `R$ ${item.calculatedPay.toFixed(2)}`,
          ];
        });

        autoTable(doc, {
          startY: currentY,
          head: [['Nome', 'Tipo de Remuneração', 'Base de Cálculo', 'Total a Pagar']],
          body: payrollRows,
          theme: 'striped',
          headStyles: { fillColor: [59, 48, 54], textColor: [255, 255, 255], fontStyle: 'bold' },
          bodyStyles: { textColor: [59, 48, 54] },
          alternateRowStyles: { fillColor: [252, 249, 249] },
          margin: { left: margin, right: margin },
        });

        currentY = (doc as any).lastAutoTable.finalY + 12;
      }

      // 6. Cash Flow Transactions Detail
      if (cfData && cfData.length > 0) {
        if (currentY > 200) {
          doc.addPage();
          currentY = 20;
        }
        currentY = drawSectionHeader('5. Detalhamento de Transações (Fluxo de Caixa)', currentY);

        const cfRows = cfData.map((cf) => {
          const typeStr = cf.type === 'INCOME' ? 'Entrada' : 'Saída';
          const formattedDate = new Date(cf.date).toLocaleDateString('pt-BR');
          return [
            formattedDate,
            cf.description || 'Sem descrição',
            typeStr,
            `R$ ${cf.amount.toFixed(2)}`,
          ];
        });

        autoTable(doc, {
          startY: currentY,
          head: [['Data', 'Descrição', 'Tipo', 'Valor']],
          body: cfRows,
          theme: 'striped',
          headStyles: { fillColor: [59, 48, 54], textColor: [255, 255, 255], fontStyle: 'bold' },
          bodyStyles: { textColor: [59, 48, 54] },
          alternateRowStyles: { fillColor: [252, 249, 249] },
          margin: { left: margin, right: margin },
        });
      }

      // 7. Draw page numbers and footers across all pages
      const totalPages = doc.getNumberOfPages();
      for (let i = 1; i <= totalPages; i++) {
        doc.setPage(i);

        // Subtle footer divider line
        doc.setDrawColor(234, 225, 225); // #eae1e1
        doc.setLineWidth(0.5);
        doc.line(margin, 280, docWidth - margin, 280);

        // Footer texts
        doc.setFont('helvetica', 'normal');
        doc.setFontSize(8);
        doc.setTextColor(122, 112, 116);
        doc.text('Espaço Cristiane Moura  |  Relatório de Desempenho', margin, 285);
        doc.text(`Página ${i} de ${totalPages}`, docWidth - margin, 285, { align: 'right' });
      }

      const fileDate = new Date().toISOString().substring(0, 10);
      doc.save(`relatorio-espaco-cristiane-moura-${fileDate}.pdf`);
    } catch (err) {
      const msg = getApiErrorMessage(err, 'Erro ao gerar PDF');
      await showError(msg);
    } finally {
      setIsGeneratingPDF(false);
    }
  };

  const chartDataEmployee = Object.entries(appointments?.byEmployee || {}).map(([name, count]) => ({
    name,
    Agendamentos: count,
  }));
  const chartDataService = Object.entries(appointments?.byService || {}).map(([name, count]) => ({
    name,
    Agendamentos: count,
  }));

  const revenueByDate: Record<string, number> = {};
  cashFlows.forEach((cf) => {
    if (cf.type === 'INCOME') {
      const d = cf.date.substring(0, 10);
      revenueByDate[d] = (revenueByDate[d] || 0) + cf.amount;
    }
  });
  const chartDataRevenue = Object.entries(revenueByDate)
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([date, amount]) => ({
      Data: new Date(date).toLocaleDateString('pt-BR', { day: '2-digit', month: '2-digit' }),
      Receita: amount,
    }));

  const formatBRL = (value: number) => {
    return new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(value);
  };

  return (
    <div className="space-y-8 animate-fade-in-up">
      <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4">
        <h2 className="font-heading text-2xl font-bold text-[#3b3036]">Relatórios</h2>
        <button
          onClick={generatePDF}
          disabled={isLoading || isGeneratingPDF}
          className="flex items-center gap-2 px-5 py-2.5 bg-[#be8a83] hover:bg-[#a1706a] text-white font-semibold text-sm rounded-xl transition-all shadow-md shadow-[#be8a83]/10 disabled:opacity-50 disabled:pointer-events-none cursor-pointer"
        >
          {isGeneratingPDF ? (
            <>
              <Loader2 className="animate-spin" size={18} />
              Gerando PDF...
            </>
          ) : (
            <>
              <Download size={18} />
              Exportar PDF
            </>
          )}
        </button>
      </div>

      {/* Filters */}
      <div className="flex flex-wrap gap-4 items-end bg-white/80 backdrop-blur-md rounded-2xl border border-[#eae1e1]/80 p-5 shadow-sm">
        <div className="space-y-1">
          <label className={labelCls}>De</label>
          <input
            type="date"
            value={dateFrom}
            max={dateTo || undefined}
            onChange={(e) => setDateFrom(e.target.value)}
            className={inputCls}
          />
        </div>
        <div className="space-y-1">
          <label className={labelCls}>Até</label>
          <input
            type="date"
            value={dateTo}
            min={dateFrom || undefined}
            onChange={(e) => setDateTo(e.target.value)}
            className={inputCls}
          />
        </div>
        <button
          onClick={() => {
            setDateFrom('');
            setDateTo('');
          }}
          className="px-5 py-2.5 border border-[#eae1e1] text-sm font-semibold text-[#3b3036] hover:text-[#be8a83] hover:border-[#be8a83] bg-white rounded-xl transition-all duration-200 cursor-pointer"
        >
          Mês Atual (Padrão)
        </button>
      </div>

      {/* Modern UX/UI Tabs */}
      <div className="flex border-b border-[#eae1e1] gap-8">
        {[
          { id: 'financial', label: 'Finanças & Receitas' },
          { id: 'payroll', label: 'Folha de Pagamento' },
          { id: 'appointments', label: 'Agendamentos' },
          { id: 'pricing', label: 'Preço por Serviço' },
        ].map((tab) => (
          <button
            key={tab.id}
            onClick={() => setActiveTab(tab.id as any)}
            className={`pb-3 text-sm font-semibold border-b-2 transition-all cursor-pointer ${
              activeTab === tab.id
                ? 'border-[#be8a83] text-[#be8a83]'
                : 'border-transparent text-[#7a7074] hover:text-[#be8a83]'
            }`}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {isLoading ? (
        <div className="flex flex-col items-center justify-center py-20 gap-3">
          <div className="animate-spin rounded-full h-8 w-8 border-t-2 border-b-2 border-[#be8a83]"></div>
          <p className="text-sm text-[#3b3036]/60 font-medium">Gerando relatórios...</p>
        </div>
      ) : (
        <>
          {/* TAB 1: Financial summary and details */}
          {activeTab === 'financial' && (
            <div className="space-y-8 animate-fade-in-up">
              {/* Financial Summary Cards */}
              <div className="space-y-6">
                <h4 className="font-heading font-bold text-lg text-[#3b3036]">
                  Resumo Financeiro{' '}
                  <span className="text-sm font-normal text-[#7a7074]">({financial?.period})</span>
                </h4>
                <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-5 gap-4">
                  <div className="bg-white rounded-2xl border border-[#eae1e1]/80 p-5 shadow-sm transition-all duration-300 relative overflow-hidden group">
                    <div className="absolute top-0 left-0 right-0 h-1 bg-gradient-to-r from-emerald-400 to-emerald-500" />
                    <p className="text-[10px] font-bold text-[#7a7074] uppercase tracking-wider mb-1">
                      Total Receitas
                    </p>
                    <p className="text-xl font-extrabold text-emerald-600">
                      {formatBRL(financial?.totalIncome ?? 0)}
                    </p>
                  </div>
                  <div className="bg-white rounded-2xl border border-[#eae1e1]/80 p-5 shadow-sm transition-all duration-300 relative overflow-hidden group">
                    <div className="absolute top-0 left-0 right-0 h-1 bg-gradient-to-r from-rose-400 to-rose-500" />
                    <p className="text-[10px] font-bold text-[#7a7074] uppercase tracking-wider mb-1">
                      Despesas Gerais
                    </p>
                    <p className="text-xl font-extrabold text-rose-600">
                      {formatBRL(financial?.totalExpense ?? 0)}
                    </p>
                  </div>
                  <div className="bg-white rounded-2xl border border-[#eae1e1]/80 p-5 shadow-sm transition-all duration-300 relative overflow-hidden group">
                    <div className="absolute top-0 left-0 right-0 h-1 bg-gradient-to-r from-indigo-400 to-indigo-500" />
                    <p className="text-[10px] font-bold text-[#7a7074] uppercase tracking-wider mb-1">
                      Salários Fixos
                    </p>
                    <p className="text-xl font-extrabold text-indigo-600">
                      {formatBRL(financial?.totalSalaryPaid ?? 0)}
                    </p>
                  </div>
                  <div className="bg-white rounded-2xl border border-[#eae1e1]/80 p-5 shadow-sm transition-all duration-300 relative overflow-hidden group">
                    <div className="absolute top-0 left-0 right-0 h-1 bg-gradient-to-r from-amber-400 to-amber-500" />
                    <p className="text-[10px] font-bold text-[#7a7074] uppercase tracking-wider mb-1">
                      Comissões
                    </p>
                    <p className="text-xl font-extrabold text-amber-600">
                      {formatBRL(financial?.totalCommissionPaid ?? 0)}
                    </p>
                  </div>
                  <div className="bg-white rounded-2xl border border-[#eae1e1]/80 p-5 shadow-sm transition-all duration-300 relative overflow-hidden group">
                    <div className="absolute top-0 left-0 right-0 h-1 bg-gradient-to-r from-[#be8a83] to-[#e5a49c]" />
                    <p className="text-[10px] font-bold text-[#7a7074] uppercase tracking-wider mb-1">
                      Lucro Líquido
                    </p>
                    <p
                      className={`text-xl font-extrabold ${(financial?.netProfit ?? 0) >= 0 ? 'text-[#be8a83]' : 'text-rose-600'}`}
                    >
                      {formatBRL(financial?.netProfit ?? 0)}
                    </p>
                  </div>
                </div>
              </div>

              {/* Employee Remuneration breakdown table */}
              {financial?.employeeFinanceDetails && financial.employeeFinanceDetails.length > 0 && (
                <div className="space-y-4">
                  <h4 className="font-heading font-bold text-lg text-[#3b3036]">
                    Detalhamento de Remunerações por Funcionário(a)
                  </h4>
                  <div className="bg-white rounded-2xl border border-[#eae1e1]/80 shadow-sm overflow-hidden">
                    <div className="overflow-x-auto">
                      <table className="w-full text-left border-collapse min-w-[600px]">
                        <thead>
                          <tr className="bg-[#fcf9f9]/50 border-b border-[#eae1e1]">
                            <th className="px-6 py-4 text-xs font-semibold text-[#3b3036] font-heading uppercase tracking-wider">
                              Nome
                            </th>
                            <th className="px-6 py-4 text-xs font-semibold text-[#3b3036] font-heading uppercase tracking-wider">
                              Modelo
                            </th>
                            <th className="px-6 py-4 text-xs font-semibold text-[#3b3036] font-heading uppercase tracking-wider">
                              Base
                            </th>
                            <th className="px-6 py-4 text-xs font-semibold text-[#3b3036] font-heading uppercase tracking-wider text-center">
                              Atendimentos
                            </th>
                            <th className="px-6 py-4 text-xs font-semibold text-[#3b3036] font-heading uppercase tracking-wider text-right">
                              Valor Atendimentos
                            </th>
                            <th className="px-6 py-4 text-xs font-semibold text-[#3b3036] font-heading uppercase tracking-wider text-right">
                              Valor A Pagar
                            </th>
                          </tr>
                        </thead>
                        <tbody className="divide-y divide-[#eae1e1]/40">
                          {financial.employeeFinanceDetails.map((emp) => {
                            let typeStr = 'Não definido';
                            let baseStr = 'R$ 0,00';

                            if (emp.remunerationType === 'SALARIO_FIXO') {
                              typeStr = 'Salário Fixo';
                              baseStr = formatBRL(emp.remunerationValue ?? 0);
                            } else if (emp.remunerationType === 'COMISSIONADO') {
                              typeStr = 'Comissionado';
                              const scopeStr =
                                emp.commissionScope === 'GLOBAL' ? 'Global' : 'Individual';
                              baseStr = `${emp.remunerationValue ?? 0}% (${scopeStr})`;
                            } else if (emp.remunerationType === 'FIXO_E_COMISSIONADO') {
                              typeStr = 'Fixo + Comissionado';
                              const scopeStr =
                                emp.commissionScope === 'GLOBAL' ? 'Global' : 'Individual';
                              baseStr = `${formatBRL(emp.remunerationValue ?? 0)} + ${(emp.commissionValue ?? 0).toFixed(0)}% (${scopeStr})`;
                            }

                            return (
                              <tr
                                key={emp.employeeId}
                                className="hover:bg-[#fcf9f9]/20 transition-colors"
                              >
                                <td className="px-6 py-4 text-sm font-semibold text-[#3b3036]">
                                  {emp.employeeName}
                                </td>
                                <td className="px-6 py-4 text-sm text-[#7a7074]">{typeStr}</td>
                                <td className="px-6 py-4 text-sm text-[#7a7074]">{baseStr}</td>
                                <td className="px-6 py-4 text-sm text-[#7a7074] text-center">
                                  {emp.doneAppointmentsCount}
                                </td>
                                <td className="px-6 py-4 text-sm text-[#7a7074] text-right">
                                  {formatBRL(emp.doneAppointmentsValue ?? 0)}
                                </td>
                                <td className="px-6 py-4 text-sm font-bold text-[#be8a83] text-right">
                                  {formatBRL(emp.calculatedPayout ?? 0)}
                                </td>
                              </tr>
                            );
                          })}
                        </tbody>
                      </table>
                    </div>
                  </div>
                </div>
              )}

              {/* Per-professional financial history */}
              <EmployeeFinancialHistorySection
                employees={financial?.employeeFinanceDetails ?? []}
                dateFrom={dateFrom}
                dateTo={dateTo}
              />

              {/* Revenue Chart */}
              <div>
                <h4 className="font-heading font-bold text-lg text-[#3b3036] mb-4">
                  Evolução de Receitas
                </h4>
                <div className="bg-white rounded-2xl border border-[#eae1e1]/80 p-6 shadow-sm">
                  <div style={{ height: 300, width: '100%' }}>
                    {chartDataRevenue.length > 0 ? (
                      <ResponsiveContainer>
                        <LineChart data={chartDataRevenue}>
                          <CartesianGrid strokeDasharray="3 3" stroke="#f3f4f6" />
                          <XAxis dataKey="Data" tick={{ fontSize: 12 }} />
                          <YAxis tick={{ fontSize: 12 }} />
                          <Tooltip formatter={(val: any) => formatBRL(Number(val ?? 0))} />
                          <Legend />
                          <Line
                            type="monotone"
                            dataKey="Receita"
                            stroke="#be8a83"
                            strokeWidth={2}
                            activeDot={{ r: 8 }}
                          />
                        </LineChart>
                      </ResponsiveContainer>
                    ) : (
                      <div className="flex items-center justify-center h-full text-sm text-gray-400">
                        Nenhuma receita no período selecionado.
                      </div>
                    )}
                  </div>
                </div>
              </div>
            </div>
          )}

          {/* TAB 2: Payroll & Commissions */}
          {activeTab === 'payroll' && (
            <div className="space-y-6 animate-fade-in-up">
              <div className="flex flex-col gap-2">
                <h4 className="font-heading font-bold text-lg text-[#3b3036]">
                  Demonstrativo de Folha de Pagamento
                </h4>
                <p className="text-xs text-[#7a7074]">
                  Cálculo dos valores finais a pagar baseados no regime de contratação e faturamento
                  por profissional.
                </p>
              </div>

              <div className="bg-white rounded-2xl border border-[#eae1e1]/80 shadow-sm overflow-hidden">
                <div className="overflow-x-auto">
                  <table className="w-full text-left border-collapse min-w-[700px]">
                    <thead>
                      <tr className="bg-[#fcf9f9]/50 border-b border-[#eae1e1]">
                        <th className="px-6 py-4 text-xs font-semibold text-[#3b3036] font-heading uppercase tracking-wider">
                          Profissional
                        </th>
                        <th className="px-6 py-4 text-xs font-semibold text-[#3b3036] font-heading uppercase tracking-wider">
                          Regime Contratual
                        </th>
                        <th className="px-6 py-4 text-xs font-semibold text-[#3b3036] font-heading uppercase tracking-wider">
                          Escopo de Comissão
                        </th>
                        <th className="px-6 py-4 text-xs font-semibold text-[#3b3036] font-heading uppercase tracking-wider text-right">
                          Base de Cálculo
                        </th>
                        <th className="px-6 py-4 text-xs font-semibold text-[#3b3036] font-heading uppercase tracking-wider text-right">
                          Total a Pagar
                        </th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-[#eae1e1]/40">
                      {payroll && payroll.items && payroll.items.length > 0 ? (
                        payroll.items.map((item) => {
                          return (
                            <tr
                              key={item.employeeId}
                              className="hover:bg-[#fcf9f9]/20 transition-colors"
                            >
                              <td className="px-6 py-4 text-sm font-semibold text-[#3b3036]">
                                {item.employeeName}
                              </td>

                              {/* Remuneration Type Badge */}
                              <td className="px-6 py-4 text-sm">
                                {item.remunerationType === 'SALARIO_FIXO' && (
                                  <span className="px-2.5 py-1 text-xs font-semibold rounded-full bg-gray-100 text-gray-700 dark:bg-gray-800 dark:text-gray-300">
                                    Salário Fixo
                                  </span>
                                )}
                                {item.remunerationType === 'COMISSIONADO' && (
                                  <span className="px-2.5 py-1 text-xs font-semibold rounded-full bg-emerald-50 text-emerald-700 border border-emerald-200">
                                    Comissionado
                                  </span>
                                )}
                                {item.remunerationType === 'FIXO_E_COMISSIONADO' && (
                                  <span className="px-2.5 py-1 text-xs font-semibold rounded-full bg-indigo-50 text-indigo-700 border border-indigo-200">
                                    Misto (Fixo + Com.)
                                  </span>
                                )}
                                {!item.remunerationType && <span className="text-gray-400">-</span>}
                              </td>

                              {/* Commission Scope Badge */}
                              <td className="px-6 py-4 text-sm">
                                {item.commissionScope === 'INDIVIDUAL' && (
                                  <span className="px-2.5 py-1 text-xs font-semibold rounded-full bg-amber-50 text-amber-700 border border-amber-200">
                                    Individual
                                  </span>
                                )}
                                {item.commissionScope === 'GLOBAL' && (
                                  <span className="px-2.5 py-1 text-xs font-semibold rounded-full bg-sky-50 text-sky-700 border border-sky-200">
                                    Global
                                  </span>
                                )}
                                {!item.commissionScope && (
                                  <span className="text-gray-400 font-medium">-</span>
                                )}
                              </td>

                              {/* Base Amount */}
                              <td className="px-6 py-4 text-sm text-[#7a7074] text-right">
                                {formatBRL(item.baseAmount)}
                              </td>

                              {/* Calculated Payout Highlight */}
                              <td className="px-6 py-4 text-sm font-extrabold text-[#be8a83] text-right">
                                {formatBRL(item.calculatedPay)}
                              </td>
                            </tr>
                          );
                        })
                      ) : (
                        <tr>
                          <td colSpan={5} className="px-6 py-10 text-center text-sm text-[#7a7074]">
                            Nenhum registro de pagamento encontrado para o período selecionado.
                          </td>
                        </tr>
                      )}
                    </tbody>
                  </table>
                </div>
              </div>
            </div>
          )}

          {/* TAB 3: Appointment statistics */}
          {activeTab === 'appointments' && (
            <div className="space-y-8 animate-fade-in-up">
              {/* Appointment Stats Summary */}
              <div>
                <h4 className="font-heading font-bold text-lg text-[#3b3036] mb-4">
                  Desempenho de Agendamentos{' '}
                  <span className="text-sm font-normal text-gray-400">
                    ({appointments?.period})
                  </span>
                </h4>
                <div className="grid grid-cols-2 md:grid-cols-4 gap-5 mb-8">
                  {[
                    {
                      label: 'Total',
                      value: appointments?.totalAppointments,
                      color: 'text-indigo-600',
                      stripe: 'from-indigo-400 to-indigo-500',
                    },
                    {
                      label: 'Concluídos',
                      value: appointments?.done,
                      color: 'text-emerald-600',
                      stripe: 'from-emerald-400 to-emerald-500',
                    },
                    {
                      label: 'Pendentes',
                      value: appointments?.pending,
                      color: 'text-amber-600',
                      stripe: 'from-amber-400 to-amber-500',
                    },
                    {
                      label: 'Cancelados',
                      value: appointments?.cancelled,
                      color: 'text-rose-600',
                      stripe: 'from-rose-400 to-rose-500',
                    },
                  ].map((stat, i) => (
                    <div
                      key={i}
                      className="bg-white rounded-2xl border border-[#eae1e1]/80 p-5 text-center shadow-sm relative overflow-hidden group transition-all duration-300"
                    >
                      <div
                        className={`absolute top-0 left-0 right-0 h-1 bg-gradient-to-r ${stat.stripe}`}
                      />
                      <p className={`text-4xl font-extrabold ${stat.color}`}>{stat.value}</p>
                      <p className="text-xs font-semibold text-[#7a7074] uppercase tracking-wider mt-1">
                        {stat.label}
                      </p>
                    </div>
                  ))}
                </div>

                <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                  <div className="bg-white rounded-2xl border border-[#eae1e1]/80 shadow-sm overflow-hidden">
                    <div className="px-5 py-4 border-b border-[#eae1e1] bg-[#fcf9f9]/50">
                      <span className="font-semibold text-xs text-[#3b3036] font-heading uppercase tracking-wider">
                        Por Profissional
                      </span>
                    </div>
                    <div className="p-4" style={{ height: 300 }}>
                      {chartDataEmployee.length > 0 ? (
                        <ResponsiveContainer>
                          <BarChart data={chartDataEmployee}>
                            <CartesianGrid strokeDasharray="3 3" stroke="#f3f4f6" />
                            <XAxis dataKey="name" tick={{ fontSize: 11 }} />
                            <YAxis tick={{ fontSize: 11 }} />
                            <Tooltip />
                            <Bar dataKey="Agendamentos" fill="#be8a83" radius={[4, 4, 0, 0]} />
                          </BarChart>
                        </ResponsiveContainer>
                      ) : (
                        <div className="flex items-center justify-center h-full text-sm text-gray-400">
                          Nenhum dado
                        </div>
                      )}
                    </div>
                  </div>
                  <div className="bg-white rounded-2xl border border-[#eae1e1]/80 shadow-sm overflow-hidden">
                    <div className="px-5 py-4 border-b border-[#eae1e1] bg-[#fcf9f9]/50">
                      <span className="font-semibold text-xs text-[#3b3036] font-heading uppercase tracking-wider">
                        Por Serviço
                      </span>
                    </div>
                    <div className="p-4" style={{ height: 300 }}>
                      {chartDataService.length > 0 ? (
                        <ResponsiveContainer>
                          <BarChart data={chartDataService} layout="vertical">
                            <CartesianGrid strokeDasharray="3 3" stroke="#f3f4f6" />
                            <XAxis type="number" tick={{ fontSize: 11 }} />
                            <YAxis
                              dataKey="name"
                              type="category"
                              width={100}
                              tick={{ fontSize: 11 }}
                            />
                            <Tooltip />
                            <Bar dataKey="Agendamentos" fill="#3b3036" radius={[0, 4, 4, 0]} />
                          </BarChart>
                        </ResponsiveContainer>
                      ) : (
                        <div className="flex items-center justify-center h-full text-sm text-gray-400">
                          Nenhum dado
                        </div>
                      )}
                    </div>
                  </div>
                </div>
              </div>
            </div>
          )}

          {/* TAB 4: Pricing analysis per service type, with fixed expense allocation */}
          {activeTab === 'pricing' && (
            <div className="space-y-6 animate-fade-in-up">
              <div className="flex flex-col gap-2">
                <h4 className="font-heading font-bold text-lg text-[#3b3036]">
                  Estou cobrando certo por cada serviço?
                </h4>
                <p className="text-xs text-[#7a7074]">
                  Pra cada serviço realmente executado no período, mostra quanto ele rendeu menos
                  custo de produtos usados, comissão da profissional e a fatia proporcional dos
                  gastos fixos (aluguel, água, luz...) — os serviços com margem mais apertada
                  aparecem primeiro.
                </p>
                {servicePricing && (
                  <p className="text-xs text-[#7a7074]">
                    Gastos fixos rateados no período:{' '}
                    <span className="font-semibold text-[#3b3036]">
                      {formatBRL(servicePricing.totalFixedExpenses)}
                    </span>
                  </p>
                )}
              </div>

              <div className="bg-white rounded-2xl border border-[#eae1e1]/80 shadow-sm overflow-hidden">
                <div className="overflow-x-auto">
                  <table className="w-full text-left border-collapse min-w-[900px]">
                    <thead>
                      <tr className="bg-[#fcf9f9]/50 border-b border-[#eae1e1]">
                        <th className="px-6 py-4 text-xs font-semibold text-[#3b3036] font-heading uppercase tracking-wider">
                          Serviço
                        </th>
                        <th className="px-6 py-4 text-xs font-semibold text-[#3b3036] font-heading uppercase tracking-wider text-center">
                          Vezes
                        </th>
                        <th className="px-6 py-4 text-xs font-semibold text-[#3b3036] font-heading uppercase tracking-wider text-right">
                          Receita
                        </th>
                        <th className="px-6 py-4 text-xs font-semibold text-[#3b3036] font-heading uppercase tracking-wider text-right">
                          Custo Produtos
                        </th>
                        <th className="px-6 py-4 text-xs font-semibold text-[#3b3036] font-heading uppercase tracking-wider text-right">
                          Comissão
                        </th>
                        <th className="px-6 py-4 text-xs font-semibold text-[#3b3036] font-heading uppercase tracking-wider text-right">
                          Rateio Gastos Fixos
                        </th>
                        <th className="px-6 py-4 text-xs font-semibold text-[#3b3036] font-heading uppercase tracking-wider text-right">
                          Lucro Líquido
                        </th>
                        <th className="px-6 py-4 text-xs font-semibold text-[#3b3036] font-heading uppercase tracking-wider text-right">
                          Margem
                        </th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-[#eae1e1]/40">
                      {servicePricing && servicePricing.items.length > 0 ? (
                        servicePricing.items.map((item) => (
                          <tr
                            key={item.serviceId}
                            className={`transition-colors ${!item.healthy ? 'bg-rose-50/40 hover:bg-rose-50/70' : 'hover:bg-[#fcf9f9]/20'}`}
                          >
                            <td className="px-6 py-4 text-sm font-semibold text-[#3b3036]">
                              <div className="flex items-center gap-2">
                                {!item.healthy && (
                                  <span title="Margem negativa — considere reajustar o preço">
                                    <AlertTriangle size={14} className="text-rose-500 shrink-0" />
                                  </span>
                                )}
                                {item.serviceName}
                              </div>
                            </td>
                            <td className="px-6 py-4 text-sm text-[#7a7074] text-center">
                              {item.timesPerformed}
                            </td>
                            <td className="px-6 py-4 text-sm text-[#7a7074] text-right">
                              {formatBRL(item.totalRevenue)}
                            </td>
                            <td className="px-6 py-4 text-sm text-[#7a7074] text-right">
                              {formatBRL(item.recipeCostTotal)}
                            </td>
                            <td className="px-6 py-4 text-sm text-[#7a7074] text-right">
                              {formatBRL(item.commissionCostTotal)}
                            </td>
                            <td className="px-6 py-4 text-sm text-[#7a7074] text-right">
                              {formatBRL(item.fixedExpenseShare)}
                            </td>
                            <td
                              className={`px-6 py-4 text-sm font-bold text-right ${item.healthy ? 'text-emerald-600' : 'text-rose-600'}`}
                            >
                              <div className="flex items-center justify-end gap-1">
                                {item.healthy ? (
                                  <TrendingUp size={14} />
                                ) : (
                                  <TrendingDown size={14} />
                                )}
                                {formatBRL(item.netProfit)}
                              </div>
                            </td>
                            <td
                              className={`px-6 py-4 text-sm font-bold text-right ${item.healthy ? 'text-emerald-600' : 'text-rose-600'}`}
                            >
                              {item.marginPercent.toFixed(1)}%
                            </td>
                          </tr>
                        ))
                      ) : (
                        <tr>
                          <td colSpan={8} className="px-6 py-10 text-center text-sm text-[#7a7074]">
                            Nenhum serviço concluído no período selecionado.
                          </td>
                        </tr>
                      )}
                    </tbody>
                  </table>
                </div>
              </div>
            </div>
          )}
        </>
      )}
    </div>
  );
};
