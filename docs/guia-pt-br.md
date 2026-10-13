# LedgerVoice — guia do projeto

## Objetivo e evolução

Este projeto atende ao desafio DIO de uma API de orçamento com Spring AI: receber áudio, transcrever, interpretar uma intenção, executar uma função da aplicação, persistir ou consultar dados e produzir uma resposta.

A evolução escolhida é a revisão antes da gravação. Uma fala como “gastei 42 reais e 90 centavos no almoço hoje” pode virar uma proposta de despesa. O usuário confere valor, categoria, descrição e data. Apenas a confirmação explícita transforma a proposta em lançamento.

O projeto usa implementação própria, com a trilha DIO como referência. Não presume que o usuário assistiu a todas as aulas nem atribui experiência de produção a esta demonstração.

## O que foi construído

- Spring Boot 4.1.1 e Spring AI 2.0.1, com uma base executada em JDK 17.
- `ChatClient`, ferramentas anotadas com `@Tool`, transcrição e geração de MP3.
- Regras de valor monetário, categoria, descrição e data reutilizadas pelos caminhos manual e IA.
- Propostas com validade de 15 minutos e confirmação repetível por ID.
- Persistência em H2 de arquivo, JDBC com parâmetros e migração Flyway.
- Auditoria gravada na mesma transação de banco que confirma o lançamento.
- Consultas de mês/categoria, paginação e totais somente de registros confirmados.
- Testes de domínio, HTTP, concorrência, rollback e contrato dos adaptadores Spring AI.

## Execute primeiro sem chave

Na pasta do projeto:

```bash
bash ./mvnw clean verify
java -jar target/ledgervoice-1.0.0.jar
```

No Windows, use `mvnw.cmd clean verify`. A primeira execução precisa baixar dependências. A API abre em `http://localhost:8080`, com acesso local. O banco será criado em `var/` e preservado quando a aplicação reiniciar.

O modo demo aceita uma gramática explícita, e não simula um modelo inteligente:

```text
despesa;42,90;FOOD;Almoço;hoje
receita;3500.00;SALARY;Salário;2026-10-01
resumo;2026-10
listar;2026-10;ALL
```

Envie o comando para `POST /api/v1/assistant/commands` no formato `{"text":"..."}`. Datas explícitas devem ser de 2000 até hoje; ajuste o mês das consultas quando necessário. Áudio e MP3 no demo retornam `503` com uma explicação, sem produzir conteúdo fictício.

## Demonstração para apresentar

1. Prepare uma despesa pelo modo demo.
2. Mostre `requiresConfirmation: true` e o estado `PENDING` da proposta.
3. Consulte `/api/v1/summary` e observe que a proposta ainda não participa dos totais.
4. Revise os campos, copie o ID e chame `/api/v1/proposals/{id}/confirm` com POST.
5. Mostre o `201`, o registro criado e o valor no resumo.
6. Repita a confirmação: o `200` e `Confirmation-Replayed: true` devolvem o mesmo registro.
7. Consulte `/api/v1/proposals/{id}/audit` e explique as ações registradas.

As requisições estão em [requests.http](requests.http). O endpoint manual de propostas usa as mesmas regras que a IA. Não é necessário expor a aplicação na internet para demonstrar o trabalho.

## Ative a IA real quando tiver acesso ao provedor

Defina `OPENAI_API_KEY` no ambiente do servidor e execute com `--spring.profiles.active=live`. O [README](../README.md) inclui comandos para Bash e PowerShell e descreve os modelos configuráveis.

Nesse modo, o áudio vai para o serviço de transcrição, o texto passa pelo `ChatClient` e o modelo escolhe uma ferramenta permitida. O servidor devolve o resultado real dessa ferramenta. A resposta final da API não depende de uma frase do modelo dizendo que algo foi salvo.

Para ler a resposta em voz, envie o texto a `/api/v1/assistant/speech`. A voz é gerada por IA, com identificação no cabeçalho. Transcrição, interpretação e síntese são operações externas distintas e podem gerar cobrança. Nenhuma chave real acompanha a entrega.

## Como explicar as decisões

| Pergunta | Resposta verificável no código |
|---|---|
| A IA pode confirmar sozinha? | Não. A ferramenta de confirmação não está registrada. Ela apenas prepara propostas e consulta dados. |
| Onde os valores são validados? | `TransactionDraft.validate`, chamado no caso de uso, além da validação HTTP. |
| Por que usar `BigDecimal`? | O orçamento exige valores decimais exatos; o cálculo financeiro não usa ponto flutuante. |
| Como evitar confirmação duplicada? | Bloqueio da linha da proposta, restrição única no banco e consulta do registro já confirmado. |
| E se a auditoria falhar? | A transação inteira é desfeita; há um teste com falha injetada no caminho JDBC real. |
| Os testes realmente usam Spring AI? | Sim. O adaptador, o `ChatClient`, as ferramentas e os codecs HTTP são executados contra um provedor local de respostas controladas. |
| Esses testes provam qualidade da IA? | Não. Eles verificam o contrato; a interpretação e a qualidade de voz com modelos reais precisam de avaliação separada. |

## Conceitos praticados

Este trabalho conecta ferramentas da IA a casos de uso, preserva uma fronteira de confirmação e mostra como validar argumentos externos antes de persistir. Também permite estudar transações, rollback, bloqueio de linhas, migrações, erros HTTP e testes sem depender de chaves pagas.

Para registrar seu próprio aprendizado, execute o fluxo, leia os testes e anote as decisões que consegue explicar. Não transforme resultados de teste local em alegações de uso em empresas ou de qualidade de modelos reais.

## Limites da entrega

A API é para uso local e um único usuário. Não tem login, autorização por usuário, conversão cambial, integração bancária ou memória de conversa. H2 de arquivo deve ser usado por um processo por vez. Repetir um comando pode criar outra proposta; a repetição protegida é a confirmação do mesmo ID.

O banco guarda descrições e valores dos registros. A aplicação não retém áudio nem transcrições no banco; o provedor externo tem suas próprias políticas. Use dados fictícios na demonstração. Consulte [SECURITY.md](../SECURITY.md) e [architecture.md](architecture.md).

## Entrega na DIO

Depois da publicação, envie a URL principal do repositório público `ricciarellidev/ledgervoice-spring-ai`. Confira README, fontes, testes, documentação e a execução real do workflow. Os resultados executados estão em [quality-report.md](quality-report.md).

Descrição sugerida:

> LedgerVoice é uma API de orçamento em Java, Spring Boot e Spring AI que interpreta comandos de texto e voz com Tool Calling. A evolução implementada prepara lançamentos para revisão antes da confirmação, com validações de domínio, persistência, auditoria transacional e confirmação repetível. O projeto inclui modo demo sem chave, testes automatizados, contrato OpenAPI e documentação das decisões e limites.
