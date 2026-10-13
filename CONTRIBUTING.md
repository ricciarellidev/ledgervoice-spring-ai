# Contributing

Run `bash ./mvnw --batch-mode --no-transfer-progress clean verify` before submitting a change. The suite must stay independent of real provider credentials and network calls to OpenAI.

Preserve the review boundary: an AI tool can prepare a proposal or query confirmed transactions, never confirm a proposal. Keep money decimal, pass external input through the same domain rules, and keep persistence plus audit in one transaction.

For a public API change, update docs/openapi.yaml and docs/requests.http. Add a regression test for a changed behavior. New migrations should be additive; do not rewrite an applied migration to conceal a schema change. Explain limits honestly and do not commit databases, real audio, API keys or customer data.
