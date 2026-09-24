#!/usr/bin/env bash
# One-time setup of a fresh Ubuntu/Debian OVH VPS for PlantPal.
# Run as the default sudo user (OVH: `ubuntu` or `debian`):
#   sudo bash bootstrap.sh
# Idempotent — safe to re-run.
set -euo pipefail

DEPLOY_USER=deploy
APP_DIR=/opt/plantpal
SUDO_USER_NAME="${SUDO_USER:-root}"

[ "$(id -u)" -eq 0 ] || { echo "run with sudo"; exit 1; }

echo "==> System packages + automatic security updates"
apt-get update -y
DEBIAN_FRONTEND=noninteractive apt-get upgrade -y
DEBIAN_FRONTEND=noninteractive apt-get install -y ca-certificates curl ufw fail2ban unattended-upgrades
dpkg-reconfigure -f noninteractive unattended-upgrades

echo "==> Docker Engine + compose plugin"
if ! command -v docker >/dev/null; then
  curl -fsSL https://get.docker.com | sh
fi
systemctl enable --now docker

echo "==> Swap (2G) so the JVM + Postgres survive memory spikes on small plans"
if ! swapon --show | grep -q /swapfile; then
  fallocate -l 2G /swapfile
  chmod 600 /swapfile
  mkswap /swapfile
  swapon /swapfile
  grep -q '/swapfile' /etc/fstab || echo '/swapfile none swap sw 0 0' >> /etc/fstab
fi

echo "==> '${DEPLOY_USER}' user (used by CI to deploy; member of docker group)"
id "$DEPLOY_USER" >/dev/null 2>&1 || adduser --disabled-password --gecos "" "$DEPLOY_USER"
usermod -aG docker "$DEPLOY_USER"
install -d -m 700 -o "$DEPLOY_USER" -g "$DEPLOY_USER" "/home/$DEPLOY_USER/.ssh"
touch "/home/$DEPLOY_USER/.ssh/authorized_keys"
chown "$DEPLOY_USER:$DEPLOY_USER" "/home/$DEPLOY_USER/.ssh/authorized_keys"
chmod 600 "/home/$DEPLOY_USER/.ssh/authorized_keys"

echo "==> App directory ${APP_DIR}"
install -d -o "$DEPLOY_USER" -g "$DEPLOY_USER" "$APP_DIR" "$APP_DIR/backend" "$APP_DIR/backups"

echo "==> Firewall: SSH, HTTP, HTTPS only"
ufw allow OpenSSH
ufw allow 80/tcp
ufw allow 443/tcp
ufw allow 443/udp
ufw --force enable

echo "==> Nightly Postgres backup (03:30, 14 days kept)"
cat > /etc/cron.d/plantpal-backup <<EOF
30 3 * * * ${DEPLOY_USER} cd ${APP_DIR} && docker compose exec -T postgres pg_dump -U plantpal plantpal | gzip > backups/plantpal-\$(date +\%F).sql.gz && find backups -name '*.sql.gz' -mtime +14 -delete
EOF

echo "==> SSH hardening"
# Only disable password login once the admin user can already log in with a key,
# otherwise this script would lock you out.
if [ -s "/home/${SUDO_USER_NAME}/.ssh/authorized_keys" ] || [ -s /root/.ssh/authorized_keys ]; then
  cat > /etc/ssh/sshd_config.d/99-plantpal.conf <<EOF
PasswordAuthentication no
KbdInteractiveAuthentication no
PermitRootLogin no
EOF
  systemctl reload ssh 2>/dev/null || systemctl reload sshd
  echo "    password login disabled (key-only)."
else
  echo "    SKIPPED: no SSH key found for ${SUDO_USER_NAME}. Add yours with ssh-copy-id, then re-run."
fi

echo
echo "Done. Next: add the CI deploy key to /home/${DEPLOY_USER}/.ssh/authorized_keys"
echo "and create ${APP_DIR}/.env (see .env.example)."
