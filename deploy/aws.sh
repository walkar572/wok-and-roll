#!/usr/bin/env bash
# Wok & Roll on AWS: EC2 (Docker) + RDS (PostgreSQL) + Secrets Manager, read with the EC2 IAM role.
# On the EC2 server, from the root of the repository:
#   ./deploy/aws.sh setup       once: Docker, Compose, AWS CLI, swap (then log out and in again)
#   ./deploy/aws.sh start       read the secret, build and start the site and the API
#   ./deploy/aws.sh update      git pull, then start
#   ./deploy/aws.sh status      the containers and a check of the API
#   ./deploy/aws.sh logs [back|web]
#   ./deploy/aws.sh passwords   new password for the public demo accounts admin, staff and viewer
#   ./deploy/aws.sh stop        stop the site and the API (the database on RDS stays)
#
# The secret (Secrets Manager, "Other type of secret", key/value), default name wok-and-roll/prod:
#   DB_HOST         the endpoint of the RDS database
#   DB_PASSWORD     the master password of RDS
#   JWT_SECRET      a long random text (openssl rand -base64 48)
#   optional: DB_USERNAME (wokroll), DB_NAME (wokroll), DB_PORT (5432), GEMINI_API_KEY or
#   ANTHROPIC_API_KEY (Panko's AI chat), SITE_ADDRESS (a host name for HTTPS, see the Caddyfile)
# Settings: SECRET_ID (the name of the secret), AWS_REGION (default: the region of this server).
set -euo pipefail

cd "$(dirname "$0")/.."
COMPOSE=(docker compose -f docker-compose.aws.yml)
SECRET_ID="${SECRET_ID:-wok-and-roll/prod}"
KEYS="DB_HOST DB_PORT DB_NAME DB_USERNAME DB_PASSWORD DB_SSLMODE JWT_SECRET GEMINI_API_KEY ANTHROPIC_API_KEY ASSISTANT_PROVIDER ASSISTANT_MODEL ASSISTANT_EFFORT SITE_ADDRESS"

say() { printf '\n== %s\n' "$*"; }
fail() {
  printf '\nError: %s\n' "$*" >&2
  exit 1
}

# A value of the instance metadata (IMDSv2); empty outside EC2.
metadata() {
  local token
  token=$(curl -s -m 2 -X PUT http://169.254.169.254/latest/api/token \
    -H 'X-aws-ec2-metadata-token-ttl-seconds: 60' || true)
  [ -n "$token" ] || return 0
  curl -s -m 2 -H "X-aws-ec2-metadata-token: $token" \
    "http://169.254.169.254/latest/meta-data/$1" || true
}

# Reads the secret with the role of the server and exports its keys for docker compose.
load_secret() {
  command -v aws >/dev/null || fail "no AWS CLI: run ./deploy/aws.sh setup"
  local region json
  region="${AWS_REGION:-$(metadata placement/region)}"
  [ -n "$region" ] || fail "unknown region: run on EC2 or set AWS_REGION"
  json=$(aws secretsmanager get-secret-value --secret-id "$SECRET_ID" --region "$region" \
    --query SecretString --output text) ||
    fail "cannot read the secret $SECRET_ID in $region. Is the IAM role attached to this server, and is the secret in this region?"
  eval "$(KEYS="$KEYS" python3 -c '
import json, os, shlex, sys
data = json.loads(sys.stdin.read())
for key in os.environ["KEYS"].split():
    value = data.get(key)
    if value not in (None, ""):
        print("export %s=%s" % (key, shlex.quote(str(value))))
' <<<"$json")"
  local key
  for key in DB_HOST DB_PASSWORD JWT_SECRET; do
    [ -n "${!key:-}" ] || fail "the secret $SECRET_ID has no $key"
  done
}

# HTTPS with a host name, else plain HTTP on the address of the server.
has_host_name() { [ -n "${SITE_ADDRESS:-}" ] && [ "${SITE_ADDRESS#:}" = "$SITE_ADDRESS" ]; }

api_status() {
  if has_host_name; then
    curl -sk -m 5 -o /dev/null -w '%{http_code}' --resolve "$SITE_ADDRESS:443:127.0.0.1" \
      "https://$SITE_ADDRESS/api/v1/countries" || true
  else
    curl -s -m 5 -o /dev/null -w '%{http_code}' http://localhost/api/v1/countries || true
  fi
}

site_url() {
  if has_host_name; then
    echo "https://$SITE_ADDRESS"
  else
    local ip
    ip=$(metadata public-ipv4)
    echo "http://${ip:-localhost}"
  fi
}

wait_api() {
  printf 'Waiting for the API'
  local _
  for _ in $(seq 1 90); do
    if [ "$(api_status)" = 200 ]; then
      echo ' - ready'
      return 0
    fi
    printf '.'
    sleep 2
  done
  echo
  fail "the API did not answer in 3 minutes. See: ./deploy/aws.sh logs back"
}

setup() {
  say "Docker, Compose, git, AWS CLI"
  sudo apt-get update -y
  sudo apt-get install -y docker.io docker-compose-v2 git curl python3
  command -v aws >/dev/null || sudo snap install aws-cli --classic
  sudo usermod -aG docker "$USER"
  if ! swapon --show | grep -q /swapfile; then
    say "2 GB of swap for the builds"
    sudo fallocate -l 2G /swapfile
    sudo chmod 600 /swapfile
    sudo mkswap /swapfile
    sudo swapon /swapfile
    grep -q '^/swapfile' /etc/fstab || echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab >/dev/null
  fi
  say "Done. Log out (exit) and connect again, then run: ./deploy/aws.sh start"
}

start() {
  load_secret
  say "Building and starting the site and the API (the first time takes 5-10 minutes)"
  "${COMPOSE[@]}" up -d --build --remove-orphans
  wait_api
  say "The site is up: $(site_url)"
}

status() {
  "${COMPOSE[@]}" ps
  load_secret
  echo "API: $(api_status) (200 is fine) - $(site_url)"
}

passwords() {
  load_secret
  local password="${NEW_PASSWORD:-}"
  if [ -z "$password" ]; then
    read -rsp 'New password for admin, staff and viewer (at least 8 characters): ' password
    echo
  fi
  [ "${#password}" -ge 8 ] || fail "the password is shorter than 8 characters"
  docker run --rm -i -e PGPASSWORD="$DB_PASSWORD" -e NEW_PASSWORD="$password" postgres:16 \
    psql -q -v ON_ERROR_STOP=1 \
    "host=$DB_HOST port=${DB_PORT:-5432} dbname=${DB_NAME:-wokroll} user=${DB_USERNAME:-wokroll} sslmode=${DB_SSLMODE:-require}" <<'SQL'
\getenv pw NEW_PASSWORD
CREATE EXTENSION IF NOT EXISTS pgcrypto;
UPDATE app_user SET password_hash = crypt(:'pw', gen_salt('bf', 10))
WHERE email IN ('admin@wokandroll.test', 'staff@wokandroll.test', 'viewer@wokandroll.test');
SQL
  say "admin, staff and viewer have the new password; the customer account keeps the demo one"
}

case "${1:-}" in
  setup) setup ;;
  start) start ;;
  update)
    git pull --ff-only
    start
    ;;
  status) status ;;
  logs) "${COMPOSE[@]}" logs -f --tail=200 "${2:-back}" ;;
  passwords) passwords ;;
  stop) "${COMPOSE[@]}" down ;;
  *)
    sed -n '2,10p' "$0" | sed 's/^# \{0,1\}//'
    exit 1
    ;;
esac
