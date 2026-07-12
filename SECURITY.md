# Security policy

Tweebyte is a research and benchmarking system, not a hosted service or a hardened public deployment. It includes production-shaped controls so the two backend stacks can be compared under realistic conditions, but its bundled infrastructure uses development authorities and trust roots.

## Supported version

There are no tagged releases. Only the current `main` branch is supported.

## Security boundary

- The `prod` and `functional-equivalence` runtime paths enable Keycloak-issued JWT validation, gateway ownership checks, TLS and east-west mTLS, Vault-backed secrets, actuator authentication, rate limiting, CORS, security headers, and structured audit events. [`ARCHITECTURE.md`](ARCHITECTURE.md#security-posture) owns the design and profile matrix.
- Keycloak, Vault, and the certificate authority bundled with the repository run in development mode. Their defaults and generated trust roots are not suitable for an internet-facing deployment.
- The `benchmark` profile intentionally disables or bypasses request-path security. Load tools call backend services directly over plain HTTP on ports 9091–9093. Never expose a benchmark-profile process or native-local benchmark infrastructure to an untrusted network.
- Local secrets belong in the gitignored `.env`; [`.env.example`](.env.example) is the committed template. Certificates and keys are generated locally by `deployment/docker-compose/tls/gen-certs.sh` and must not be committed. Rotate any credential that is disclosed.
- Dependencies are pinned for reproducibility rather than automatic freshness. CI runs Trivy and OWASP Dependency-Check and emits a CycloneDX SBOM, but those checks are not a security guarantee.

## Reporting a vulnerability

Do not open a public issue containing vulnerability details.

Email the maintainer at [andrei.zbarcea@gmail.com](mailto:andrei.zbarcea@gmail.com). If private vulnerability reporting is enabled for the repository, GitHub's **Security → Report a vulnerability** flow may also be used: [open a private advisory](https://github.com/andreizb/tweebyte/security/advisories/new).

Include:

- the affected stack and runtime profile;
- the affected commit and component;
- reproduction steps or a minimal proof of concept;
- the observed and expected behavior;
- the likely impact and any suggested mitigation.

Reports are handled on a best-effort basis. There is no security SLA or guaranteed fix timeline. Please allow time to reproduce and coordinate a fix before public disclosure.
