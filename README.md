# 💇‍♀️ Salon - Espaço Cristiane Moura

**Serviço de Gestão e Agendamento para Salão de Beleza**

Sistema para gerenciamento de salão de beleza. Abrange operações administrativas, agendamento online, gestão de equipe e relatórios financeiros.

---

## 🚀 Destaques Técnicos

O sistema foi construído utilizando padrões modernos de desenvolvimento:

- **Log de Auditoria:**
  - **Backend:** Interceptação de requisições usando a anotação `@Auditable` e filtro HTTP. Captura IP real, User-Agent e mascara dados sensíveis (senhas, cartões) antes de salvar no banco.
  - **Frontend:** Console administrativo com filtros combinados e leitor de JSON com _syntax highlighting_.
- **Integração com Serviços Externos (Resend API & Mercado Pago):**
  - **E-mails Transacionais (Resend API):** Envio de confirmações, cancelamentos e lembrete D-1 de agendamento em segundo plano (`@Async`) usando templates Thymeleaf e o `RestClient` do Spring.
  - **Pagamentos via PIX (Mercado Pago API):** Geração de QR Code e Pix Copia e Cola (Checkout Transparente) com coleta JIT (Just-In-Time) de CPF (validação por Módulo 11) e Webhooks protegidos por assinatura de segurança (`x-signature` via HMAC-SHA256) para conciliação automática.
- **Suporte a PWA (Progressive Web App):**
  - O frontend foi construído como um PWA (Progressive Web App) utilizando `vite-plugin-pwa`. Isso permite a instalação local do aplicativo, cache inteligente de recursos estáticos com Workbox e funcionamento offline-first da interface, com estratégias *NetworkFirst* de cache para rotas públicas e feature flags.
  - **Notificações Push (Web Push API):** com o PWA instalado, o usuário recebe notificação nativa do sistema operacional (mesmo com o app fechado) nos mesmos eventos que já disparam e-mail — ver seção [🔔 Notificações Push](#-notificações-push-web-push-api) para os detalhes completos.
- **Testes de Qualidade e Cobertura Comprovável:**
  - **Backend (JaCoCo - Linhas: 92.78% | Instruções: 93.56% | Branches: 78.61%):** Testes unitários/integração com JUnit 5 e Mockito. Relatório de cobertura disponível em [cobertura/backend/index.html](./cobertura/backend/index.html).
  - **Frontend (Vitest - Linhas: 99.15% | Branches: 91.87%):** Relatório de cobertura disponível em [cobertura/frontend/index.html](./cobertura/frontend/index.html).

---

## 🎬 Vídeo de Demonstração

Gravamos um vídeo demonstrando o sistema em funcionamento — fluxos de agendamento, painel administrativo, integrações e demais funcionalidades descritas neste README.

📺 **Assista aqui:** [Apresentando sistema para salão de beleza](https://youtu.be/dAna434OWtg)

---

## 📝 Log de Auditoria

O sistema de auditoria registra ações críticas de gravação ou autenticação realizadas no sistema.

- **O que é auditado:**
  - Operações de escrita em entidades de negócio (criação, edição e exclusão de produtos, serviços, funcionários, clientes e agendamentos).
  - Controle de acesso (login de usuários, geração de tokens, atualização/inativação de contas).
  - Alterações de configurações de infraestrutura (estados das Feature Flags).
  - Execução de pagamentos via PIX.
- **Onde fica armazenado:**
  - Os logs são persistidos na tabela relacional `tb_audit_log` no banco de dados.
  - **Principais campos:** `id` (PK), `user_email` (e-mail do operador ou `GUEST`), `action` (descrição da ação/HTTP método e endpoint), `entity_type` (nome da classe/entidade afetada), `entity_id` (identificador do registro modificado), `status` (resultado da operação: `SUCCESS` ou `FAILURE`), `ip_address` (IP de origem resolvendo cabeçalhos como `X-Forwarded-For`), `user_agent` (navegador e OS do cliente), `created_at` (timestamp local de America/Recife).
- **Como foi implementado:**
  - **Programação Orientada a Aspectos (AOP):** Utilização da anotação customizada `@Auditable` em métodos de escrita nos controladores. O aspecto `AuditAspect` intercepta a execução, resolve o resultado (se houve sucesso ou se uma exceção foi disparada) e grava o log de forma assíncrona.
  - **Filtro HTTP (Spring Security Filter):** O filtro `AuditRequestFilter` intercepta todas as requisições HTTP para registrar acessos a endpoints e mapear dados da requisição (IP, User-Agent, cabeçalhos).
- **Classes e arquivos participantes:**
  - [AuditAspect.java](./salon-back/src/main/java/com/cristiane/salon/aspect/AuditAspect.java) (Aspecto interceptor)
  - [Auditable.java](./salon-back/src/main/java/com/cristiane/salon/aspect/Auditable.java) (Anotação de controle)
  - [AuditRequestFilter.java](./salon-back/src/main/java/com/cristiane/salon/security/AuditRequestFilter.java) (Filtro de requisição HTTP)
  - [AuditLogService.java](./salon-back/src/main/java/com/cristiane/salon/models/audit/AuditLogService.java) (Serviço de negócio)
  - [AuditLog.java](./salon-back/src/main/java/com/cristiane/salon/models/audit/AuditLog.java) (Entidade JPA)

---

## 🔗 Integração com Serviço Externo

O sistema integra-se com serviços de e-mail e gateways de pagamento em produção.

- **Serviços Externos Utilizados:**
  1. **Resend API:** Envio de e-mails transacionais (solicitações, confirmações, cancelamentos e lembrete D-1 de agendamento).
  2. **Mercado Pago API (PIX):** Checkout transparente para geração JIT de chaves PIX (cópia e cola) e QR Codes, além de recepção de Webhooks para atualização automatizada do status da reserva.
  3. **Provedor de IA (via proxy LiteLLM):** gera as recomendações financeiras/de retenção do painel admin — ver seção [🤖 Recomendações de IA e Servidor MCP](#-recomendações-de-ia-e-servidor-mcp) para os detalhes completos (é tratado à parte por ter documentação própria).
  4. **ViaCEP:** autopreenchimento de endereço (rua/bairro/cidade/UF) a partir do CEP na tela de Cadastro de Equipe — puramente conveniência de UX: se a chamada falhar, o formulário segue funcionando normalmente e a pessoa digita o endereço à mão.
- **Para que são usados:**
  - O **Resend** envia notificações automáticas em segundo plano aos clientes e administradores em eventos chave da agenda.
  - O **Mercado Pago** gerencia a cobrança de reservas, garantindo conciliação bancária imediata e conciliação segura via Webhooks.
- **Como são configurados (Variáveis de Ambiente):**
  - `MAIL_API_URL` e `MAIL_PASSWORD` (API Key do Resend).
  - `MP_ACCESS_TOKEN` (Access Token da conta do Mercado Pago).
  - `MP_WEBHOOK_SECRET` (Chave de criptografia secreta usada para validar a assinatura `x-signature` das requisições via HMAC-SHA256).
- **Classes e arquivos participantes:**
  - [EmailService.java](./salon-back/src/main/java/com/cristiane/salon/integrations/email/service/EmailService.java) (Integração Resend)
  - [MercadoPagoPaymentService.java](./salon-back/src/main/java/com/cristiane/salon/integrations/payment/service/MercadoPagoPaymentService.java) (Comunicação com SDK Mercado Pago e assinatura digital)
  - [MercadoPagoWebhookController.java](./salon-back/src/main/java/com/cristiane/salon/integrations/payment/controller/MercadoPagoWebhookController.java) (Endpoint de retorno e conciliação do PIX)

---

## 🛡️ Resiliência a Falha de Sistemas Externos

O sistema depende de três serviços externos (Mercado Pago, provedor de e-mail Resend, provedor de IA) e nenhum deles pode derrubar ou travar o resto da aplicação se sair do ar. Isso é resolvido com uma infraestrutura genérica e reaproveitável, não com tratamento de erro reescrito a cada integração:

- **Timeout compartilhado:** todo cliente HTTP para um sistema externo usa o mesmo `RestClient.Builder` configurado com timeout de conexão/leitura ([HttpClientConfig.java](./salon-back/src/main/java/com/cristiane/salon/config/HttpClientConfig.java)), evitando que uma dependência lenta prenda uma thread da aplicação indefinidamente e esgote o pool sob carga. O SDK do Mercado Pago (que gerencia seu próprio HTTP client) recebe o mesmo timeout via `MercadoPagoConfig` ([MercadoPagoConfiguration.java](./salon-back/src/main/java/com/cristiane/salon/integrations/payment/MercadoPagoConfiguration.java)).
- **Circuit Breaker + Retry ([Resilience4j](https://resilience4j.readme.io/)):** cada integração tem uma *instance* nomeada em `application.yaml` que herda de um template `default` compartilhado (limiar de falha, quantas tentativas, tempo de espera) — uma integração nova não precisa reimplementar nada, só criar uma instance referenciando `base-config: default` e anotar o método com `@CircuitBreaker`/`@Retry`. Exceções que são recusa de negócio (ex.: `MPApiException` do Mercado Pago recusando um CPF inválido, `IllegalStateException` de uma resposta de IA fora do schema) são explicitamente ignoradas nessas configs — não é falha do provedor, então não deve nem tentar de novo nem contar para abrir o circuito.
- **Gateway isolado por integração:** `MercadoPagoGateway`, `EmailGateway` e `OpenAiCompatibleChatClient` são o único ponto de contato com cada sistema externo, cada um com as anotações de resiliência. Isso não é só organização — anotações do Resilience4j são aplicadas via proxy do Spring, e uma chamada de um método para outro dentro da MESMA classe não passa pelo proxy (self-invocation), então a lógica teria que ficar isolada de qualquer forma. O serviço de negócio (`MercadoPagoPaymentService`, `EmailService`, `RecommendationService`) continua sendo quem decide o que fazer com a falha.
- **Degradação graciosa por integração:**
  - **E-mail:** já era `@Async` e falha é só registrada em auditoria — um Resend fora do ar nunca impede a criação/confirmação de um agendamento, só atrasa a notificação. Além disso, todo envio agora passa pela fila de retry descrita abaixo.
  - **Mercado Pago:** falha (timeout, circuito aberto, recusa de negócio) vira `BadRequestException` — o cliente recebe um erro claro em vez do sistema travar esperando resposta.
  - **IA:** já era isolado da lógica de negócio principal (dashboard de recomendações); falha vira `BusinessException` sem afetar agendamentos, financeiro ou qualquer outra tela.
- **Idempotência na criação de pagamento:** `@Retry` pressupõe que repetir a chamada é seguro — verdade para uma consulta, mas não para *criar* um PIX: se o Mercado Pago processar o pagamento e a resposta se perder por timeout, uma tentativa automática subsequente criaria um SEGUNDO PIX para o mesmo agendamento (cobrança duplicada real). `MercadoPagoGateway.createPayment` gera uma chave de idempotência (`X-Idempotency-Key`) uma única vez por chamada de `createPixPayment`, reaproveitada em todas as tentativas automáticas do Resilience4j daquela mesma chamada — o Mercado Pago reconhece as repetições como a mesma operação e devolve o pagamento já criado em vez de processar de novo.
- **Testado simulando falha real:** [ResiliencePatternsTest.java](./salon-back/src/test/java/com/cristiane/salon/config/resilience/ResiliencePatternsTest.java) força um "serviço externo" a falhar repetidamente e comprova que o retry tenta de novo, que o circuito abre depois do limiar configurado e passa a falhar rápido (sem sequer chamar o serviço), e que exceções de negócio são corretamente ignoradas — o mesmo comportamento documentado acima, não só a configuração.
- **Classes e arquivos participantes:**
  - [HttpClientConfig.java](./salon-back/src/main/java/com/cristiane/salon/config/HttpClientConfig.java) (timeout HTTP compartilhado)
  - [MercadoPagoGateway.java](./salon-back/src/main/java/com/cristiane/salon/integrations/payment/service/MercadoPagoGateway.java) / [EmailGateway.java](./salon-back/src/main/java/com/cristiane/salon/integrations/email/service/EmailGateway.java) / [OpenAiCompatibleChatClient.java](./salon-back/src/main/java/com/cristiane/salon/models/ai/client/OpenAiCompatibleChatClient.java)
  - `resilience4j.*` em [application.yaml](./salon-back/src/main/resources/application.yaml) (configuração central nomeada)
  - [ResiliencePatternsTest.java](./salon-back/src/test/java/com/cristiane/salon/config/resilience/ResiliencePatternsTest.java)

### 📬 Fila de e-mail (outbox) e retenção

O Circuit Breaker/Retry acima resolve blips curtos (Resend cai por 1-2 segundos). Não resolve uma queda **sustentada** (minutos/horas): sem mais nada, um e-mail que falha depois de esgotar as tentativas rápidas é perdido para sempre — o cliente nunca recebe a confirmação, e ninguém percebe além de uma linha `FAILURE` no log de auditoria. Para isso existe uma fila de retry dedicada (`tb_email_outbox`), com uma decisão deliberada de **não duplicar** o que o log de auditoria já faz:

- **O que ela guarda, e por que não é o audit log:** o audit log (`tb_audit_log`, ação `EMAIL_SENT`) já é o registro permanente e passivo de todo envio — isso não muda. A fila de outbox tem um propósito diferente e mais estreito: saber o que ainda falta reenviar, e dar visibilidade de curto prazo pro admin. Por isso a retenção dela é curta, não permanente.
- **Ritmo do retry automático:** um job (`EmailOutboxService.retryDuePending`, `@Scheduled` a cada 5 minutos) só *verifica* se algo está pronto pra nova tentativa — não é "tenta de novo a cada 5 minutos por 24h direto" (seria agressivo demais e arriscaria a reputação de envio da conta no provedor). Cada e-mail individual tem seu próprio backoff crescente: retry em 5min, 30min, 2h, 6h e 24h após a falha anterior — um total de 5 tentativas espalhadas ao longo de até ~24h. Depois disso, vira `DEAD_LETTER` (desiste automaticamente) em vez de tentar pra sempre.
- **Retenção (limpeza diária, `EmailOutboxService.cleanup`), pensada para LGPD (minimização de dado, não só espaço em disco):**

  | Status | Retenção | Por quê |
  |---|---|---|
  | `SENT` (entregue) | 7 dias | Só serve pra conferência recente na tela de admin; o registro permanente já é o audit log |
  | `FAILED` (ainda tentando) | até esgotar as tentativas (~24h) | É o trabalho em andamento |
  | `DEAD_LETTER` (desistiu) | 90 dias | Dá tempo de um admin perceber e agir manualmente (ex.: ligar pro cliente); depois disso, o agendamento em si já foi resolvido de um jeito ou de outro |

  Números configuráveis via `EMAIL_OUTBOX_SENT_RETENTION_DAYS` / `EMAIL_OUTBOX_DEAD_LETTER_RETENTION_DAYS` / `EMAIL_OUTBOX_RETRY_CHECK_INTERVAL_MS` / `EMAIL_OUTBOX_CLEANUP_CRON` (padrões acima).
- **Tela de admin (`/admin/email-outbox`, "Central de E-mails"):** lista paginada dos envios recentes (não é histórico completo — reflete a retenção acima), com filtro rápido TODOS/ENVIADO/FALHOU e botão de reenvio manual imediato (ignora o backoff) para ADMIN/SYSADMIN/GERENTE_DE_ATENDIMENTO.
- **Classes e arquivos participantes:**
  - [EmailOutboxEntry.java](./salon-back/src/main/java/com/cristiane/salon/integrations/email/outbox/entity/EmailOutboxEntry.java) (backoff e transições de status)
  - [EmailOutboxService.java](./salon-back/src/main/java/com/cristiane/salon/integrations/email/outbox/service/EmailOutboxService.java) (envio + jobs agendados de retry e limpeza)
  - [EmailOutboxController.java](./salon-back/src/main/java/com/cristiane/salon/integrations/email/outbox/controller/EmailOutboxController.java)
  - [V35\_\_create_email_outbox.sql](./salon-back/src/main/resources/db/migration/V35__create_email_outbox.sql)
  - [EmailOutbox.tsx](./salon-front/src/pages/admin/email-outbox/EmailOutbox.tsx) (tela de admin)

### 📅 Lembrete de agendamento (D-1)

Reduz no-show avisando o cliente na véspera. Um job diário (09h, horário de Recife) busca agendamentos `CONFIRMED` cujo horário cai no dia seguinte e ainda não foram lembrados, e dispara um e-mail por cliente — que passa pelo mesmo `EmailService`/fila de retry descritos acima, sem nenhum código novo de resiliência.

- **Fuso horário explícito:** a aplicação roda com timezone padrão UTC (`SalonApplication.init()`), mas o negócio é em `America/Recife` (UTC-3). "Amanhã" é calculado explicitamente nesse fuso — calcular com `LocalDate.now()` puro erraria o dia perto da meia-noite.
- **Não duplica se o job cair no meio:** cada agendamento tem uma coluna `reminded_at` (NULL = ainda não lembrado), marcada individualmente logo após disparar aquele e-mail específico — não em lote no fim do job. Se o processo reiniciar no meio da execução, os agendamentos já processados não são notificados de novo.
- **Sem link de cancelamento por token:** o e-mail linka para "Meus Agendamentos" (autenticado), não um link público de um clique — criar um endpoint de cancelamento sem login seria uma superfície de abuso nova que o resto do sistema não tem hoje (cancelamento sempre passa pelo app autenticado).
- Configurável via `APPOINTMENT_REMINDER_CRON` (padrão: `0 0 9 * * *`, horário de Recife).
- **Classes e arquivos participantes:**
  - [AppointmentReminderService.java](./salon-back/src/main/java/com/cristiane/salon/models/appointment/service/AppointmentReminderService.java) (job agendado)
  - [appointment-reminder.html](./salon-back/src/main/resources/templates/mail/appointment-reminder.html)
  - [V37\_\_add_appointment_reminded_at.sql](./salon-back/src/main/resources/db/migration/V37__add_appointment_reminded_at.sql)

---

## 🔔 Notificações Push (Web Push API)

Com o PWA instalado, o usuário recebe notificações nativas do sistema operacional mesmo com o app fechado — a mesma UI de notificação do Windows/macOS/Android, não um toast dentro do navegador. Requer o PWA instalado e HTTPS em produção (pré-requisito já atendido, ver seção de PWA acima).

- **O que gera notificação hoje** (os mesmos 4 eventos que já disparam e-mail, mais o lembrete D-1):
  1. Agendamento confirmado → push para o **cliente**.
  2. Agendamento cancelado/recusado → push para o **cliente**.
  3. Novo pedido de agendamento → push para todos os usuários **ADMIN** ativos.
  4. Pagamento PIX confirmado → push para o **cliente**.
  5. Lembrete de agendamento D-1 → push para o **cliente**, junto com o e-mail.

  Clicar na notificação abre o app direto na tela relevante (`/my-appointments` ou `/admin/appointments`).
- **iOS:** só recebe push se o PWA estiver **instalado na tela inicial** (Safari numa aba comum não implementa a Push API) e o iOS for 16.4+ — limitação da Apple, documentada no próprio código (`usePushNotification.ts`).
- **Chaves VAPID — cuidado crítico:** autenticam o servidor perante os serviços de push do navegador (FCM, Mozilla autopush, etc.). Geradas uma única vez com `npx web-push generate-vapid-keys` e **nunca regeneradas** depois: trocar a chave privada invalida instantaneamente TODAS as subscriptions já salvas no banco, e cada usuário precisaria autorizar notificações de novo. Configuração em `VAPID_PUBLIC_KEY`/`VAPID_PRIVATE_KEY`/`VAPID_SUBJECT` (backend) e `VITE_VAPID_PUBLIC_KEY` (frontend, mesma chave pública) — ver `.env.example` para instruções completas.
- **Service worker escrito à mão (`injectManifest`):** registrar os listeners de `push`/`notificationclick` exigiu trocar a estratégia do `vite-plugin-pwa` de `generateSW` (automática) para `injectManifest` — o cache `NetworkFirst` de rotas públicas que existia antes foi reescrito manualmente em `src/sw.ts` (workbox-routing) para preservar o comportamento anterior.
- **Falha isolada por assinatura, nunca bloqueia a resposta HTTP:** `PushService.sendToUser` é `@Async` e trata cada subscription (cada navegador/dispositivo autorizado) de forma independente — uma subscription expirada (HTTP 410 Gone) é removida do banco automaticamente, e falha em uma nunca impede o envio para as demais.
- **Sem Circuit Breaker aqui, ao contrário das outras integrações externas** (ver seção de Resiliência): cada envio de push vai para um endpoint diferente por assinatura (o navegador de cada usuário gera sua própria URL via FCM/Mozilla/etc.) — não é "um provedor" que pode cair inteiro como o Mercado Pago ou o Resend, então o padrão de circuito não se aplica da mesma forma.
- **Classes e arquivos participantes:**
  - [PushService.java](./salon-back/src/main/java/com/cristiane/salon/integrations/push/service/PushService.java) (envio, limpeza de subscription expirada)
  - [PushController.java](./salon-back/src/main/java/com/cristiane/salon/integrations/push/controller/PushController.java) (assinar/cancelar)
  - [WebPushConfig.java](./salon-back/src/main/java/com/cristiane/salon/integrations/push/config/WebPushConfig.java) (bean único da biblioteca `web-push`, provider BouncyCastle)
  - [V38\_\_create_push_subscription.sql](./salon-back/src/main/resources/db/migration/V38__create_push_subscription.sql)
  - [usePushNotification.ts](./salon-front/src/hooks/usePushNotification.ts) (opt-in no frontend)
  - [sw.ts](./salon-front/src/sw.ts) (service worker customizado)

---

## 🤖 Recomendações de IA e Servidor MCP

O sistema integra IA em duas frentes, construídas sobre a mesma configuração central (**Central de IA**, no painel sysadmin) e o mesmo motor de negócio — nenhuma das duas duplica lógica:

- **Motor de recomendações:** analisa dados agregados do salão (financeiro/ocupação e retenção de clientes) e devolve sugestões acionáveis numa tela do painel admin (`/admin/recommendations`). Cada geração é cacheada, contabilizada num orçamento diário de chamadas e registrada em log de auditoria próprio.
- **Servidor MCP (Model Context Protocol):** expõe esse mesmo motor como *tools* que um assistente de IA externo (Claude Desktop, Cursor, etc.) pode chamar diretamente fora da aplicação, autenticado por token próprio gerenciável no painel sysadmin.

Toda chamada passa por um proxy **LiteLLM** (compatível com a API da OpenAI) — o projeto não fala com nenhum provedor de LLM diretamente, apenas com esse proxy.

Dois controles independentes decidem se a feature está disponível:
- **Feature flag `ENABLE_AI_RECOMMENDATIONS`** (nasce desligada): controla se a página/menu existe, ligada/desligada como qualquer outra feature flag do sistema.
- **Toggle operacional da Central de IA (`AiConfig.enabled`):** liga/desliga só a chamada ao provedor (ex. para pausar custos ou trocar de chave), sem esconder a tela — os botões de gerar/atualizar somem quando desligado, evitando erro ao clicar.

Documentação de arquitetura e diretrizes de desenvolvimento em [`docs/`](./docs/).

---

## 📈 Observabilidade com OpenTelemetry

O backend é instrumentado com **OpenTelemetry**, cobrindo os três sinais de telemetria (traces, métricas e logs), enviados via OTLP para uma stack **Grafana LGTM** (Loki + Grafana + Tempo + Prometheus/Mimir) — local via `docker-compose.yml` em desenvolvimento, e para um servidor central em produção.

- **Instrumentação automática (zero-code):** um agente Java (`-javaagent`, versão fixada no `Dockerfile`) captura HTTP, JDBC e métricas da JVM em toda a aplicação, sem alterar código.
- **Instrumentação manual (spans de negócio):** spans explícitos (`@WithSpan`) adicionados seletivamente na geração do relatório financeiro (`ReportService.java`), a rota mais lenta identificada via telemetria automática — permitiu isolar o gargalo em consultas ao banco (ver detalhes no relatório).
- **Logs correlacionados:** exportação para o Loki via OTLP, com atributos estruturados do MDC capturados e correlação automática log↔trace.

O relatório da entrega, com evidências (traces reais, spans, queries SQL capturadas) e o diagnóstico do gargalo encontrado, está em [`2026-07-22-relatorio-opentelemetry.md`](./2026-07-22-relatorio-opentelemetry.md). O guia conceitual e tutorial prático (o que é telemetria, como instrumentar) está em [`docs/opentelemetry.md`](./docs/opentelemetry.md), com um complemento dedicado a logs em [`docs/opentelemetry-logs.md`](./docs/opentelemetry-logs.md).

---

## 🛠️ Stack Tecnológica

- **Backend:** Java 21 · Spring Boot 4.0.6 · Spring Security · JWT · Spring Data JPA · PostgreSQL · Flyway · JaCoCo · Lombok · Springdoc OpenAPI
- **Frontend:** React 19 · TypeScript · Vite · Vitest · Tailwind CSS v4.0 · Axios · React Router v7 · React Hook Form · Recharts · jsPDF · PWA
- **Infra:** Docker · Docker Compose · GitHub Actions · Nginx

---

## 💎 Padrões de Arquitetura

- **Soft-Deletes:** Exclusão lógica (campo `active`) para produtos e usuários, não quebrando o histórico financeiro do banco.
- **Global Exception Handler:** Centralização de erros (`@RestControllerAdvice`). Oculta metadados do banco em falhas e formata respostas de erro de forma amigável.
- **Igualdade Referencial:** Uso de `useCallback` e `useMemo` no React para evitar re-renderizações e travamentos na interface.
- **Feature Flags Dinâmicas:** Toggles no banco de dados (`CLIENT_BOOKING`, `EMAIL_NOTIFICATIONS`) que ligam/desligam funcionalidades no sistema em tempo real, sem precisar de novo deploy.
- **Circuit Breaker / Retry (Resilience4j):** Infraestrutura genérica de resiliência a falha de sistema externo — timeout compartilhado, circuito e retry configurados uma vez e reaproveitados por qualquer integração nova (ver seção [🛡️ Resiliência a Falha de Sistemas Externos](#%EF%B8%8F-resiliência-a-falha-de-sistemas-externos)).

---

## 📁 Estrutura do Monorepo

```text
projeto-eq03/
├── salon-back/      # API Spring Boot (Java 21)
├── salon-front/     # SPA React (PWA)
├── docs/            # Documentação detalhada e diagramas
├── docker-compose.yml
└── README.md
```

---

## ⚙️ Pré-requisitos e Execução Local

**Requisitos:** Java 21, Maven 3.9+, Node.js 22+, PostgreSQL 16+ (ou Docker).

```bash
# 1. Clonar o repositório
git clone https://github.com/Des-Sist-Corp-UFPB/projeto-eq03.git
cd projeto-eq03

# 2. Subir banco de dados e SMTP (via Docker ou serviços locais/IDE)
docker compose up db mailpit -d

# 3. Iniciar a API Backend
cd salon-back
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# 4. Iniciar o Frontend
cd ../salon-front
npm install
npm run dev
```

| Serviço      | URL Local                               | Descrição                  |
| :----------- | :-------------------------------------- | :------------------------- |
| **Frontend** | `http://localhost:5173`                 | Interface SPA / PWA        |
| **Backend**  | `http://localhost:8080`                 | API REST                   |
| **Swagger**  | `http://localhost:8080/swagger-ui.html` | Documentação dos Endpoints |

---

## 📜 Convenções de Código

- **DTOs:** Utilização de Java `record` (ex: `UserCreateRequest` / `UserResponse`, evitando o sufixo genérico `DTO`).
- **Entidades:** Mapeamento JPA com tabelas (`tb_`), usando Lombok apenas onde necessário.
- **Versionamento:** Endpoints versionados na base da URL (ex: `/v1/users`).
- **Segurança:** Autenticação via JWT com roles e controle de autoridade granular por endpoint e método HTTP.

---

## 📖 Documentação Adicional

Mais detalhes sobre a arquitetura do projeto, APIs, testes e diretrizes de desenvolvimento/agentes de IA estão disponíveis no diretório [`docs/`](./docs/):

- [ARCHITECTURE.md](./docs/ARCHITECTURE.md) - Estrutura de pacotes do Monorepo e padrões arquiteturais.
- [API.md](./docs/API.md) - Referência de Endpoints REST e Esquema de Banco de Dados.
- [TESTING.md](./docs/TESTING.md) - Estratégia de testes de qualidade no Backend e Frontend.
- [SECURITY.md](./docs/SECURITY.md) - Detalhes do controle de autenticação e autorização por papéis.

---

## 📊 Avaliação de Performance (k6)

Testamos com o [k6](https://k6.io/) uma única pergunta: **qual o maior número
de requisições por segundo que o sistema sustenta, com 100% de sucesso e
resposta em até 1 segundo** (p(95) ≤ 1000 ms — o limiar clássico de UX para
"sente como instantâneo")?

Os artefatos completos estão em [`loadtest/`](./loadtest/): as duas fases
exploratórias em `report-fase1-bracket.md` e `report-fase2-busca-fina.md`
(+ seus `resultado-*.json`), e o resultado final confirmado em
`loadtest/report.md` / `loadtest/resultado.json` — os nomes padrão que o
script grava quando você não sobrescreve `REPORT_PATH`/`RESULT_PATH` (ver
seção 1).

---

### 0. Como rodar

Pré-requisitos: projeto rodando localmente via `docker compose up -d` e Docker instalado (roda o k6 via imagem oficial, sem precisar instalar nada extra). Comandos abaixo em Linux/macOS (bash/zsh) — para PowerShell, troque `\` por `` ` `` no fim de cada linha; para cmd.exe, troque por `^` e `${PWD}` por `%cd%`.

O script tem dois modos, escolhidos por `MODE`, com parâmetros ajustáveis via variável de ambiente:

| Variável | Modo | Padrão | Efeito |
|---|---|---|---|
| `MODE` | — | `staircase` | `staircase` (escada) ou `soak` (RPS fixo sustentado) |
| `SLA_MS` | ambos | `1000` | Orçamento de latência (p(95)) que define "OK" |
| `STEP_START` / `STEP_INCREMENT` / `STEP_COUNT` | staircase | `20`/`20`/`10` | Faixa de RPS da escada |
| `STEP_DURATION_S` | staircase | `30` | Duração de cada degrau (segundos) |
| `SOAK_RPS` | soak | *(obrigatório)* | RPS fixo a sustentar |
| `SOAK_DURATION_S` | soak | `180` | Duração do soak (segundos) |
| `REPORT_PATH` / `RESULT_PATH` | ambos | `loadtest/report.md` / `loadtest/resultado.json` | Onde salvar a saída |

**Comandos exatos para reproduzir as 3 fases documentadas abaixo** (rode em sequência; limpe o banco entre uma fase e outra — dados de teste ficam marcados com datas `2099-*`, o `teardown()` do próprio script já cancela/exclui a maior parte, mas agendamentos cancelados continuam ocupando linhas na tabela já que a API não tem `DELETE` para esse recurso):

```bash
# Fase 1 — bracket grosso (~5 min)
docker run --rm -i --network projeto-eq03_salon-network \
  -v "${PWD}:/app" -w /app --env-file .env -e BASE_URL=http://salon-app:8080 \
  -e STEP_START=20 -e STEP_INCREMENT=20 -e STEP_COUNT=10 -e STEP_DURATION_S=25 \
  -e REPORT_PATH=loadtest/report-fase1-bracket.md -e RESULT_PATH=loadtest/resultado-fase1-bracket.json \
  grafana/k6 run loadtest/carga.js

# limpar banco (ver loadtest/README.md para o compose local),
# depois Fase 2 — busca fina na faixa de transição encontrada na fase 1 (~5 min)
docker run --rm -i --network projeto-eq03_salon-network \
  -v "${PWD}:/app" -w /app --env-file .env -e BASE_URL=http://salon-app:8080 \
  -e STEP_START=100 -e STEP_INCREMENT=5 -e STEP_COUNT=9 -e STEP_DURATION_S=30 \
  -e REPORT_PATH=loadtest/report-fase2-busca-fina.md -e RESULT_PATH=loadtest/resultado-fase2-busca-fina.json \
  grafana/k6 run loadtest/carga.js

# limpar banco de novo, depois Fase 3 — soak de confirmação no RPS candidato (~4 min)
# usa os nomes padrão (REPORT_PATH/RESULT_PATH não sobrescritos): esse É o
# resultado final, por isso vira loadtest/report.md e loadtest/resultado.json
docker run --rm -i --network projeto-eq03_salon-network \
  -v "${PWD}:/app" -w /app --env-file .env -e BASE_URL=http://salon-app:8080 \
  -e MODE=soak -e SOAK_RPS=100 -e SOAK_DURATION_S=180 \
  grafana/k6 run loadtest/carga.js
```

> O nome da rede (`projeto-eq03_salon-network`) segue o padrão `<pasta-do-projeto>_<nome-da-rede-no-compose>`. Se o diretório do projeto tiver outro nome, confira com `docker network ls`.

---

### 1. Metodologia

Fizemos 3 execuções manuais em sequência, cada uma refinando a anterior:

1. **Bracket grosso** — escada de passo largo (20 em 20 req/s) para achar
   ENTRE quais degraus o sistema quebra. → [`report-fase1-bracket.md`](./loadtest/report-fase1-bracket.md)
2. **Busca fina** — escada de passo estreito (5 em 5 req/s), só na faixa de
   transição encontrada na fase 1, para localizar o teto com precisão.
   → [`report-fase2-busca-fina.md`](./loadtest/report-fase2-busca-fina.md)
3. **Soak de confirmação** — sustenta o RPS candidato por 3 minutos
   contínuos (`MODE=soak`) para provar que é capacidade real, não sorte de
   uma janela curta. A latência é comparada entre a primeira e a segunda
   metade da janela — um RPS só é considerado confirmado se a segunda
   metade também ficar dentro do SLA (degradação progressiva reprova o
   teste, mesmo que a média geral pareça OK). **Este é o resultado final** —
   → [`report.md`](./loadtest/report.md)

Cada arquivo listado acima é a saída **genuína e não editada** do script
para aquela execução — nenhum resultado foi digitado ou combinado à mão.
O banco foi limpo entre cada execução (dados de teste marcados com datas
exclusivas de 2099, removidos via SQL) para a tabela não crescer de uma
fase para a outra e enviesar a medição.

Distribuição probabilística das **18 rotas** testadas (leitura + escrita, fluxo autenticado via JWT admin):

| Rota | Método | Tipo | Aprox. |
|---|---|---|---|
| `GET /v1/reports/financial` | GET | Relatório financeiro (agregação SQL) | 8% |
| `GET /v1/reports/appointments` | GET | Relatório de agendamentos | 7% |
| `GET /v1/reports/payroll` | GET | Relatório de folha | 4% |
| `GET /v1/appointments?status=` | GET | Agendamentos filtrados por status | 3% |
| `GET /v1/appointments` | GET | Listagem de agendamentos (paginada) | 7% |
| `GET /v1/cashflow` | GET | Extrato de caixa | 7% |
| `GET /v1/users` | GET | Listagem de usuários | 4% |
| `GET /v1/clients` | GET | Listagem de clientes | 4% |
| `GET /v1/employees/booking` | GET | Funcionários disponíveis p/ agendamento | 6% |
| `GET /v1/products` | GET | Listagem de produtos | 5% |
| `GET /v1/services` | GET | Listagem de serviços | 5% |
| `GET /ping` | GET | Health check | 5% |
| `POST /v1/appointments` | POST | Criação de agendamento | 8% |
| `POST /v1/cashflow` | POST | Lançamento de caixa | 7% |
| `POST+DELETE /v1/cashflow/{id}` | POST+DELETE | Lançamento e exclusão de caixa | 5% |
| `POST+PATCH /v1/appointments/{id}/cancel` | POST+PATCH | Criação e cancelamento de agendamento | 5% |
| `PUT /v1/products/{id}` | PUT | Atualização de produto | 5% |
| `PATCH /v1/users/{id}` | PATCH | Atualização de usuário | 5% |

Todos os dados de escrita são isolados por marcadores exclusivos de teste (data `2099-01-01`/`2099-10-15`/`2099-10-20`, usuário de teste dedicado) e removidos automaticamente por um `teardown()` ao final de cada execução — agendamentos são cancelados (a API não expõe `DELETE` para esse recurso); cashflow e usuários de teste são excluídos de fato.

---

### 2. Resultado final

✅ **O sistema sustenta 100 requisições por segundo, com 100% de sucesso e
p(95) ≤ 1000 ms, confirmado por 3 minutos de carga contínua.**

Ao longo dessa janela de confirmação, processou **19.744 requisições com
sucesso** (100%, nenhuma falha), com p(95) de 678 ms.

| Fase | Achado | p(95) | Erro | Arquivo |
|---|---|---|---|---|
| 1 — Bracket grosso (20 em 20, 25s/degrau) | teto indicado: 120 req/s (colapso em 140) | 386 ms | 0,00% | [report-fase1-bracket.md](./loadtest/report-fase1-bracket.md) |
| 2 — Busca fina (5 em 5, 30s/degrau) | teto indicado: 105 req/s (colapso em 110) | 162 ms | 0,00% | [report-fase2-busca-fina.md](./loadtest/report-fase2-busca-fina.md) |
| 3 — Soak (100 req/s, 3 min) | **confirmado** | **678 ms** | 0,00% | [report.md](./loadtest/report.md) |

> **Por que rodar 3 fases em vez de confiar direto na escada:** em execuções
> anteriores desta mesma metodologia, a busca fina chegou a indicar 150 req/s
> como aprovado (p(95)=372 ms em janelas de 30s) — mas ao sustentar esse
> valor por 3 minutos inteiros no soak, a latência subiu para p(95)≈5,9 s.
> A janela curta tinha sido sorte, não capacidade real; o mesmo aconteceu
> depois em 120 req/s (p(95)≈1,8 s no soak). Só 100 req/s se sustentou de
> forma confiável em todas as tentativas. **Isso é evidência direta de por
> que a etapa de confirmação por soak é indispensável** — sem ela, o
> relatório poderia reportar um número bem maior do que o sistema realmente
> aguenta de forma sustentada. A escada por si só serve para *localizar*
> rapidamente a região de interesse; só o soak *confirma*.
>
> Também repare que a fase 2 desta execução refinou o teto para 105 req/s
> (não 120, como a fase 1 sozinha sugeriu) — o ponto exato de colapso variou
> um pouco entre as duas fases (natural, já que estamos bem na margem de um
> recurso pequeno e sensível a variância — o pool de 5 conexões). Isso
> reforça por que o soak de 100 req/s, com folga sob os dois achados, é o
> número que efetivamente reportamos como resultado.

---

### 3. Gargalo identificado

#### 🐘 Pool de conexões do banco de dados (HikariCP) limitado a 5

O perfil `prod` (usado pela imagem Docker padrão) define `hikari.maximum-pool-size: 5` de forma fixa em `application-prod.yaml`, sem variável de ambiente para ajuste. Sob concorrência acima de ~5 requisições simultâneas dependentes do banco, threads ficam bloqueadas aguardando uma conexão livre. Isso explica tanto o colapso abrupto visto nas escadas (entre 105–140 req/s, variando um pouco entre execuções) quanto a razão de o número indicado pela escada não se sustentar sob carga contínua no soak — o pool pequeno absorve rajadas curtas de alguns segundos, mas não aguenta minutos de carga constante, e a margem exata em que ele estoura é sensível a variância (concorrência de outras conexões, GC, etc.), por isso o resultado final reportado (100 req/s) fica com folga abaixo de qualquer teto observado nas escadas.

O perfil `dev` já suporta ajuste via `${DB_POOL_SIZE:5}`, e existe um overlay `docker-compose.performance.yml` com `DB_POOL_SIZE=100` pronto para esse perfil — mas o `prod` (perfil da imagem padrão testada) ainda não tem esse parâmetro exposto.

**Melhoria sugerida:** parametrizar `maximum-pool-size` também em `application-prod.yaml` (ex.: `${DB_POOL_SIZE:20}`) e rodar este mesmo teste de novo para medir o novo teto sustentável — a expectativa é que suba substancialmente, já que CPU e memória não eram o fator limitante em nenhum momento deste teste.

