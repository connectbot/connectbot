#!/bin/bash
set -e

SSH_USER="${SSH_USER:-testuser}"
SSH_PASSWORD="${SSH_PASSWORD:-testpass123}"

# Create test user with password
useradd -m -s /bin/bash "$SSH_USER"
echo "${SSH_USER}:${SSH_PASSWORD}" | chpasswd

# Setup SSH keys for pubkey auth
USER_HOME="/home/${SSH_USER}"
mkdir -p "$USER_HOME/.ssh"
cp /tmp/authorized_keys "$USER_HOME/.ssh/authorized_keys"
chmod 700 "$USER_HOME/.ssh"
chmod 600 "$USER_HOME/.ssh/authorized_keys"
chown -R "${SSH_USER}:${SSH_USER}" "$USER_HOME/.ssh"

# Generate host keys if they don't exist
ssh-keygen -A

echo "SSH server ready for user: ${SSH_USER}"

exec "$@"
