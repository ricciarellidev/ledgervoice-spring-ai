# Verification record

Recorded on **2026-10-13** for the LedgerVoice 1.0.0 source delivery. Results below are from executed checks. Remote GitHub CI, Docker execution and real OpenAI model quality are not claimed.

## Baseline and command

| Item | Executed baseline |
|---|---|
| Runtime | OpenJDK 17.0.20, Linux x86-64 |
| Build | Maven 3.9.11 through Maven Wrapper, checksum-pinned distribution |
| Frameworks | Spring Boot 4.1.1 / Spring AI 2.0.1 |
| Test platform | JUnit Jupiter 6.0.3, managed by the Boot BOM |
| Database | H2 2.4.240, memory databases for the suite; file database for restart smoke test |
| Coverage | JaCoCo 0.8.14 |
| Command | `bash ./mvnw --batch-mode --no-transfer-progress clean verify` |
| Result | **BUILD SUCCESS** |

The final clean run also succeeded with `--offline` after dependencies were downloaded. A first build needs Maven Central. No real provider key or paid request was used. Verification-environment proxy configuration is not included in the source delivery.

## Test results

**51 test invocations · 0 failures · 0 errors · 0 skipped**

| Suite | Cases | Evidence |
|---|---:|---|
| `TransactionDraftTest` | 10 | Positive/exact monetary values, bounds, required fields, description controls and dates |
| `AudioUploadValidatorTest` | 8 | Allowlisted headers, safe provider filenames, spoofed/empty files and size limits |
| `LedgerServiceTest` | 7 | Actual JDBC confirmation, repeat/review expiry, exact totals, pagination, missing records, rollback and concurrency |
| `BudgetApiTest` | 17 | Real HTTP requests in demo mode, review/confirm/query flow, upload rejection, expiration and correlation sanitization |
| `SpringAiContractTest` | 7 | Actual Spring AI models, ChatClient/tool execution, transcription/speech codecs, query results and upstream errors against a local HTTP fixture |
| `ApiExceptionHandlerTest` | 2 | Sanitized unexpected/framework errors and trace preservation |

Parameterized examples count individually. Three support classes are fixtures, not additional test suites.

The concurrency case starts **8 confirmation requests through 8 worker threads**, with a five-connection pool, and verifies one new receipt and one database transaction record for the proposal. It is not a throughput or distributed load benchmark.

The rollback case injects an audit failure into a JDBC repository subclass and calls the transaction-managed service. It checks that neither the ledger row nor the confirmed proposal link survives, while the earlier proposal creation audit remains. A subsequent retry succeeds.

## What the Spring AI tests establish

The live-profile tests use Spring AI's actual OpenAI adapters and a loopback HTTP server returning controlled wire responses. A tool-call response executes `prepareTransaction`; it creates a real database proposal and cannot commit a ledger record. Tool schema inspection checks that confirmation/deletion capabilities are absent.

The suite also verifies transcription forwarding with a server-generated filename, MP3 byte handling and AI-origin header, monthly totals/list queries, invalid arguments, unsupported tools, sanitized provider failures and disabled transport retries. A model message claiming that an expense was saved without a tool call is not trusted.

The WAV/MP3 fixture bytes are synthetic protocol data. They are not recordings for an actual speech engine and do not prove decoding or voice quality. No real-model accuracy, transcription quality, prompt-injection robustness across models or voice naturalness score is reported. [evaluation-cases.md](evaluation-cases.md) is an explicitly unexecuted live worksheet.

## Scoped coverage

The final clean run produced these JaCoCo aggregate counters:

| Counter | Covered / total | Ratio | Gate |
|---|---:|---:|---:|
| Lines | 302 / 310 | **97.42%** | 90% |
| Branches | 132 / 157 | **84.08%** | 75% |
| Instructions | 2159 / 2192 | 98.49% | Informational |

The measured bundle has 32 instrumented classes. `LedgerVoiceApplication`, `config/**` and `web/dto/**` are excluded in `pom.xml`; their wiring and serialization are exercised indirectly by HTTP/live-profile tests. The percentage does not apply to every line in the repository. Remaining branches are not claimed as covered.

Generated reports after a build:

- `target/surefire-reports/`
- `target/site/jacoco/index.html`
- `target/site/jacoco/jacoco.xml`

These generated files and the executable JAR are excluded from the source ZIP; the build recreates them.

## Packaged JAR and persistent restart

The repackaged JAR was started in demo mode against a fresh H2 file database. The smoke check verified:

1. Health returned `UP`.
2. A proposal was prepared and excluded from ledger totals.
3. First confirmation returned `201`.
4. Repeating it returned `200` with identical receipt bytes.
5. The process was stopped and restarted against the same file.
6. Repeating confirmation after restart returned `200` with the same receipt.
7. The database contained one confirmed expense and four audit events after the replay checks.

Both Java processes were stopped. This is a clean process restart test, not a crash-corruption, filesystem-failure or distributed recovery test.

## Additional review and limits

- The OpenAPI 3.1 document passed schema validation with `openapi-spec-validator` 0.7.2. This validates the contract structure, not all possible runtime payloads.
- All 12 domain source files were checked for absence of Spring/Jakarta/HTTP/infrastructure imports.
- Local documentation links were checked, and the SVG banner was rendered and visually reviewed.
- CI uses verified upstream v6 action commit SHAs, read-only repository permissions and the same clean verification command. Remote execution must be checked after publication.
- Flyway emitted an upstream compatibility notice: H2 2.4.240 is newer than its latest verified H2 version, 2.3.232. The Boot-managed version was retained; migration, database operations and restart checks passed. The notice was not hidden or treated as a certification of every database feature.
- Docker build/Compose, Windows wrapper execution, extra JDK versions, dependency vulnerability scanning, penetration testing and production load were not executed.
- There is no authentication, multi-user isolation, bank integration or public deployment in this delivery. H2 file mode is limited to one application process.

Passing tests support the recorded behaviors. They do not establish production readiness, security certification or defect-free software.
