#!/bin/sh
# W1 Vault seeding. Writes the application secrets into Vault KV v2 (secret/tweebyte)
# from environment variables sourced from the gitignored .env. Idempotent — a re-run
# just writes a new version of the same keys. Dev-mode Vault already mounts the KV v2
# engine at secret/, so there is no `vault secrets enable` step.
set -e

export VAULT_ADDR="${VAULT_ADDR:-http://vault:8200}"
export VAULT_TOKEN="${VAULT_TOKEN:-root}"

# Wait for the dev server to accept API calls before seeding.
i=0
until vault status >/dev/null 2>&1; do
	i=$((i + 1))
	if [ "$i" -gt 30 ]; then
		echo "vault-init: Vault did not become ready in time" >&2
		exit 1
	fi
	sleep 1
done

vault kv put secret/tweebyte \
	"spring.datasource.password=${TWEEBYTE_DB_PASSWORD}" \
	"spring.r2dbc.password=${TWEEBYTE_DB_PASSWORD}" \
	"spring.flyway.password=${TWEEBYTE_DB_PASSWORD}" \
	"app.actuator.password=${TWEEBYTE_ACTUATOR_PASSWORD}" \
	"spring.ssl.bundle.jks.tweebyte.keystore.password=${TWEEBYTE_TLS_KEYSTORE_PASSWORD}" \
	"spring.ssl.bundle.jks.tweebyte.truststore.password=${TWEEBYTE_TLS_TRUSTSTORE_PASSWORD}" \
	"app.keycloak.client-secret=${TWEEBYTE_KC_CLIENT_SECRET}" \
	"spring.ai.openai.api-key=${TWEEBYTE_LLM_API_KEY}"

echo "vault-init: seeded secret/tweebyte"
