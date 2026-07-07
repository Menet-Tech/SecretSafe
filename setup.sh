#!/bin/bash

# SecretSafe Initial Setup Script for Ubuntu Server
echo "============================================="
echo "   SecretSafe Initial Setup & Configuration  "
echo "============================================="
echo ""

# 1. Gather configuration details
read -p "Enter Target Domain (e.g. domain.example or local IP) [localhost]: " DOMAIN
DOMAIN=${DOMAIN:-localhost}

read -p "Enter Backend HTTPS Port [8051]: " PORT
PORT=${PORT:-8051}

read -p "Enter Backend API URL (e.g. https://domain.example:8051 or https://domain.example/be) [https://$DOMAIN:$PORT]: " BACKEND_URL
BACKEND_URL=${BACKEND_URL:-https://$DOMAIN:$PORT}

read -p "Enter Admin Username [admin]: " ADMIN_USER
ADMIN_USER=${ADMIN_USER:-admin}

read -p "Enter Admin Password [admin123]: " ADMIN_PASS
ADMIN_PASS=${ADMIN_PASS:-admin123}

# Load master key if already exists in .env, otherwise generate new one
if [ -f .env ]; then
    EXISTING_MASTER_KEY=$(grep '^MASTER_KEY=' .env | cut -d'=' -f2)
fi

if [ -z "$EXISTING_MASTER_KEY" ]; then
    # Generate secure 256-bit master encryption key (hex representation)
    MASTER_KEY=$(od -An -N32 -tx1 /dev/urandom | tr -d ' \n')
else
    MASTER_KEY=$EXISTING_MASTER_KEY
fi

# Extract API prefix path if present in BACKEND_URL (e.g. https://domain/be -> /be)
TEMP_URL="${BACKEND_URL#*://}"
if [[ "$TEMP_URL" == *"/"* ]]; then
    API_PREFIX="/${TEMP_URL#*/}"
    # Strip trailing slash
    API_PREFIX="${API_PREFIX%/}"
else
    API_PREFIX=""
fi

# 2. Write configurations to .env file
cat <<EOF > .env
# SecretSafe Environment Configurations
PORT=$PORT
ADMIN_USER=$ADMIN_USER
ADMIN_PASS=$ADMIN_PASS
MASTER_KEY=$MASTER_KEY
DOMAIN=$DOMAIN
BACKEND_URL=$BACKEND_URL
API_PREFIX=$API_PREFIX
EOF

# Set strict file read/write permissions for the environment file
chmod 600 .env

# 3. Dynamically generate frontend config.js
cat <<EOF > frontend/src/config.js
export const config = {
  domain: '$DOMAIN',
  backendUrl: '$BACKEND_URL'
};
EOF

echo ""
echo "============================================="
echo "   Configuration Generated Successfully!      "
echo "============================================="
echo "Files updated: .env and frontend/src/config.js"
echo ""
echo "Values Configured:"
echo "---------------------------------------------"
echo "Domain:         $DOMAIN"
echo "Port:           $PORT"
echo "Backend URL:    $BACKEND_URL"
echo "API Prefix:     $API_PREFIX"
echo "Admin User:     $ADMIN_USER"
echo "Admin Password: $ADMIN_PASS"
echo "Master Key:     (Generated and stored securely)"
echo "---------------------------------------------"
echo ""
echo "To start/apply changes, run: ./secretsafe.sh restart"
echo "============================================="
