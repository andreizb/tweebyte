#!/usr/bin/env bash
# W3 TLS: dev-CA certificate generator. Mints the PKI the prod / functional-
# equivalence profiles use for edge TLS, east-west mTLS, and TLS to Postgres +
# Redis. Run once on the host before a TLS-enabled stack (the cert/key material
# is gitignored, exactly like .env). Re-run with FORCE=1 to regenerate.
#
#   ./gen-certs.sh
#
# Output (this directory, gitignored):
#   ca.crt / ca.key                      dev root CA
#   <service>.p12                        per-service PKCS12 keystore (server + client identity)
#   truststore.p12                       shared truststore holding the CA (every peer trusts it)
#   postgres.crt / postgres.key          Postgres server cert (PEM, SAN = the three db hostnames)
#   redis.crt / redis.key                Redis server cert (PEM, SAN = redis)
#
# Keystore / truststore passwords come from the same .env the rest of W1 uses
# (TWEEBYTE_TLS_KEYSTORE_PASSWORD / TWEEBYTE_TLS_TRUSTSTORE_PASSWORD); they are
# also seeded into Vault by vault-init.sh so the apps read them at boot.
set -eu

DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$DIR"

KS_PASS="${TWEEBYTE_TLS_KEYSTORE_PASSWORD:-changeit}"
TS_PASS="${TWEEBYTE_TLS_TRUSTSTORE_PASSWORD:-changeit}"
DAYS="${TWEEBYTE_TLS_DAYS:-3650}"

if [ -f ca.crt ] && [ "${FORCE:-0}" != "1" ]; then
	echo "gen-certs: ca.crt already exists; set FORCE=1 to regenerate. Nothing to do."
	exit 0
fi

echo "gen-certs: generating dev CA + leaf certs into $DIR"
rm -f ./*.crt ./*.key ./*.csr ./*.p12 ./*.srl

# --- Root CA -----------------------------------------------------------------
openssl req -x509 -newkey rsa:2048 -nodes -sha256 -days "$DAYS" \
	-keyout ca.key -out ca.crt \
	-subj "/O=Tweebyte/CN=Tweebyte Dev CA" \
	-addext "basicConstraints=critical,CA:TRUE" \
	-addext "keyUsage=critical,keyCertSign,cRLSign"

# Issue a leaf cert usable for BOTH server TLS and mTLS client identity.
#   $1 = base name (output <name>.key/.crt/.p12)
#   $2 = subjectAltName value
#   $3 = "p12" to also emit a PKCS12 keystore, "pem" to leave key+cert as PEM
issue() {
	name="$1"
	sans="$2"
	bundle="$3"
	ext="$(mktemp)"
	cat >"$ext" <<-EXT
		subjectAltName=$sans
		basicConstraints=CA:FALSE
		keyUsage=critical,digitalSignature,keyEncipherment
		extendedKeyUsage=serverAuth,clientAuth
	EXT
	openssl req -newkey rsa:2048 -nodes -sha256 \
		-keyout "$name.key" -out "$name.csr" \
		-subj "/O=Tweebyte/CN=$name"
	openssl x509 -req -sha256 -days "$DAYS" \
		-in "$name.csr" -CA ca.crt -CAkey ca.key -CAcreateserial \
		-extfile "$ext" -out "$name.crt"
	rm -f "$name.csr" "$ext"
	if [ "$bundle" = "p12" ]; then
		openssl pkcs12 -export \
			-inkey "$name.key" -in "$name.crt" -certfile ca.crt \
			-name "$name" -out "$name.p12" -passout "pass:$KS_PASS"
		rm -f "$name.key" "$name.crt"
	fi
}

# --- Per-service keystores (server + client identity) ------------------------
for svc in gateway-service user-service tweet-service interaction-service; do
	issue "$svc" "DNS:$svc,DNS:localhost,IP:127.0.0.1" p12
done

# --- Datastore server certs (PEM, consumed natively by Postgres / Redis) -----
issue postgres "DNS:user-service-db,DNS:tweet-service-db,DNS:interaction-service-db,DNS:localhost" pem
issue redis "DNS:redis,DNS:localhost" pem
# Postgres refuses a world/group-readable private key.
chmod 600 postgres.key redis.key

# --- Shared truststore (the CA) ----------------------------------------------
# keytool writes a proper trustedCertEntry that Spring's JKS SSL bundle reads.
keytool -importcert -noprompt -alias tweebyte-ca \
	-file ca.crt -keystore truststore.p12 \
	-storetype PKCS12 -storepass "$TS_PASS"

echo "gen-certs: done. Keystores password = TWEEBYTE_TLS_KEYSTORE_PASSWORD; truststore = TWEEBYTE_TLS_TRUSTSTORE_PASSWORD."
