# Estilo de código

Código barato de ler e seguro de mudar por pessoa ou agente. Dois custos guiam: **tokens** e **probabilidade de introduzir bug**.

- **Siga o vizinho**: densidade de comentários, nomes e idioma do arquivo ao redor.
- **Localidade**: o comportamento de uma função depende dos argumentos, sem efeitos escondidos.
- **Explícito > implícito**: sem mágica de import ou auto-descoberta nova.
- **Tipos**: TypeScript sem `any`; Java com tipos específicos, sem `Exception` genérica.
- **Funções pequenas** (alvo ≤ 40 linhas, máx. 80), uma responsabilidade; service não vira "deus".
- **Uma forma canônica** por operação (um cliente HTTP `services/api`, um formatador de moeda, um jeito de alertar).
- **Sem abstração prematura**: extrair a partir da terceira duplicação.
- **Comentário explica o porquê** (regra de negócio, armadilha), nunca o quê.
- **Erros tipados**: regra de negócio lança `BusinessException` com mensagem clara; proibido engolir exceção.
- **Nomes pesquisáveis e consistentes** (`scheduledDate` sempre assim; português só em texto de interface).
- **Texto de interface** em pt-BR; identificadores de código em inglês.
- **Dinheiro**: `BigDecimal` no back, `CurrencyInput` no front. Nunca `double`/`float`.
- **Teste de bug** nomeado pelo comportamento (`cancel_removesLinkedCashFlow`).
