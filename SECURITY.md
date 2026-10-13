# Security and data handling

LedgerVoice is a single-user reference API for local learning. It has no authentication, tenant isolation, encryption at rest or production rate limiting. Keep it on loopback and use synthetic financial data. The default Java process and the Compose host port are bound to loopback.

Only proposal preparation and read operations are registered as AI tools. Confirmation is a separate REST operation. An untrusted model cannot invoke an absent capability, but confirmation is not an authentication mechanism: any client that can reach this local API can call its endpoints.

The application does not retain audio or transcripts in its database. The servlet container may use temporary multipart files during a request. Live mode sends audio, commands and relevant tool results to the configured provider. Provider retention and account policies apply; do not treat this as an offline or confidential processing service.

Store OPENAI_API_KEY only in server-side environment variables or an ignored local .env file. A .env file is automatically loaded by Docker Compose, not by native Java. Never commit it, customer data, H2 files or generated recordings. Application error responses omit credentials and internal exceptions. Application logs contain failure types and correlation IDs, not request bodies. Do not enable SDK/prompt debug logging with sensitive data.

The audio allowlist checks extensions, size and container signatures; it is not a complete decoder, antivirus scanner or media-content verification system. Live use can incur provider charges. Transport retries are disabled and each exposed tool returns directly, but transcription, chat and optional speech are separate provider operations.

For a concern, use private vulnerability reporting if enabled. Otherwise request a private channel in a minimal issue without sensitive details. A passing CI build is not a security audit. Public deployment requires authentication, authorization, network controls, rate limits, retention policies and operational review.
