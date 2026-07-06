# SecretSafe Systemd Autostart Configuration for Ubuntu

To make SecretSafe automatically start on boot on Ubuntu, we recommend setting it up as a **systemd** service. This ensures both the backend and frontend are managed, monitored, and restarted automatically if they crash.

---

## Step 1: Create the Systemd Service File

Create a new service configuration file using `nano` or your preferred text editor:

```bash
sudo nano /etc/systemd/system/secretsafe.service
```

Paste the following configuration inside. Make sure to replace `/path/to/SecretSafe` with the **absolute path** of your project directory and set the correct `User` (e.g. `ubuntu` or your current username):

```ini
[Unit]
Description=SecretSafe Security Password Manager
After=network.target

[Service]
Type=oneshot
User=ubuntu
Group=ubuntu
WorkingDirectory=/path/to/SecretSafe
ExecStart=/path/to/SecretSafe/secretsafe.sh start
ExecStop=/path/to/SecretSafe/secretsafe.sh stop
RemainAfterExit=yes
Restart=no

[Install]
WantedBy=multi-user.target
```

Save the file (`Ctrl+O`, then `Enter`) and exit (`Ctrl+X`).

---

## Step 2: Reload Systemd Daemon

Notify systemd that a new service file has been created:

```bash
sudo systemctl daemon-reload
```

---

## Step 3: Enable the Service on Boot

Enable the service so that it automatically executes every time the Ubuntu server boots up:

```bash
sudo systemctl enable secretsafe.service
```

---

## Step 4: Control the Service

You can now manage both the backend and frontend services using standard `systemctl` commands:

* **Start SecretSafe**:
  ```bash
  sudo systemctl start secretsafe
  ```
* **Stop SecretSafe**:
  ```bash
  sudo systemctl stop secretsafe
  ```
* **Restart SecretSafe**:
  ```bash
  sudo systemctl restart secretsafe
  ```
* **Check Service Status**:
  ```bash
  sudo systemctl status secretsafe
  ```

---

## Step 5: View Service Logs

Systemd pipes stdout and stderr to the journal log system. You can view real-time logs for startup issues or API errors with:

```bash
sudo journalctl -u secretsafe.service -f
```
