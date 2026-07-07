# SecretSafe 🔐

**SecretSafe** is a premium, secure, multi-user password management system. It features a high-performance **Golang HTTPS Backend**, a modern **Vite React Frontend**, and a native **Android client** equipped with biometric security (fingerprint/face verification) and custom PIN lock controls.

---

## Key Features

* **Strict HTTPS Transport**: Enforces end-to-end encrypted TLS/WSS connections, immediately rejecting plain HTTP traffic.
* **Auto-Generating TLS Certs**: Automatically provisions and generates self-signed TLS certificates (`cert.pem`/`key.pem`) on boot for secure local development and networking.
* **Biometric & PIN Security**:
  * Android biometrics (strong biometrics only) with a custom **6-digit PIN fallback** (no OS fallback) for opening the app, revealing passwords, and approving API decryption requests.
* **Developer API Keys & ACL**:
  * Generate, rotate, and manage API keys inside the web dashboard.
  * Configure Access Control Lists (ACL) to permit keys to only decrypt specific credentials.
* **Linux Daemon Controller**: Includes a dedicated shell daemon script (`secretsafe.sh`) to start, stop, restart, and check the status of your services in Linux backgrounds.
* **Systemd Autostart**: Easy service registration for automatic boot management on Ubuntu servers (`autostart.md`).

---

## Tech Stack

* **Backend**: Go (Golang) + SQLite3 + JWT + native TLS
* **Frontend**: React.js (Vite) + Tailwind CSS + Lucide Icons
* **Android**: Kotlin + Jetpack Compose + AndroidX Biometric + OkHttp3 (configured to support self-signed certificates)

---

## Deployment & Setup Guide (Ubuntu Server)

Follow these steps to deploy SecretSafe on your server:

### Step 1: Install System Dependencies
Make sure you have Git, Go, Node.js, and npm installed:
```bash
sudo apt update
sudo apt install -y git golang nodejs npm
```

### Step 2: Clone the Repository
Clone the codebase to your server:
```bash
git clone https://github.com/Menet-Tech/SecretSafe.git
cd SecretSafe
```

### Step 3: Install Frontend Packages
Install the required packages for the React web app:
```bash
cd frontend
npm install
cd ..
```

### Step 4: Run Initial Setup Script
Execute the interactive setup script to configure your environment:
```bash
chmod +x setup.sh secretsafe.sh
./setup.sh
```
This script will prompt you for:
1. **Target Domain / IP** (e.g. `sf.menet.my.id`)
2. **Backend HTTPS Port** (default: `8051`)
3. **Backend API URL** (default: `https://sf.menet.my.id:8051`)
4. **Initial Admin Credentials** (default username: `admin`, password: `admin123`)

It will generate the protected `.env` file and write the variables directly into the React config.

### Step 5: Start the Services
Start the backend and frontend dev servers:
```bash
./secretsafe.sh start
```
* On first run, this automatically compiles the Go binary version for Linux and creates TLS certificate keys.

To verify that the services are active:
```bash
./secretsafe.sh status
```

---

## Autostart on Ubuntu Boot
To configure the application to run automatically when the server boots up, refer to the detailed instructions in [autostart.md](./autostart.md) to set it up as a systemd service.

---

## Developer API Integration (cURL Example)

To retrieve a credential programmatically using an API Key:

```bash
curl -k -X GET https://[YOUR_BACKEND_ADDRESS]:8051/api/credentials/[CREDENTIAL_ID]/retrieve \
  -H "X-API-Key: ss_key_your_generated_api_key"
```
*(The `-k` flag tells cURL to ignore certificate validation if you are using self-signed TLS certificates).*

---

## License
Private / Proprietary.
