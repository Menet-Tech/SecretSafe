#!/bin/bash

# Configuration
BACKEND_DIR="./backend"
FRONTEND_DIR="./frontend"
BACKEND_PID_FILE="/tmp/secretsafe_backend.pid"
FRONTEND_PID_FILE="/tmp/secretsafe_frontend.pid"
BACKEND_LOG="/tmp/secretsafe_backend.log"
FRONTEND_LOG="/tmp/secretsafe_frontend.log"

# Clean variables from carriage returns (CRLF fallback for Linux)
BACKEND_DIR="${BACKEND_DIR%$'\r'}"
FRONTEND_DIR="${FRONTEND_DIR%$'\r'}"
BACKEND_PID_FILE="${BACKEND_PID_FILE%$'\r'}"
FRONTEND_PID_FILE="${FRONTEND_PID_FILE%$'\r'}"
BACKEND_LOG="${BACKEND_LOG%$'\r'}"
FRONTEND_LOG="${FRONTEND_LOG%$'\r'}"

# Load environment variables if .env exists
if [ -f .env ]; then
    # Export all variables from .env file, cleaning any Windows carriage returns
    export $(grep -v '^#' .env | tr -d '\r' | xargs)
fi

# Fallback to port 8051 if not defined in .env
if [ -z "$PORT" ]; then
    export PORT=8051
fi

start_backend() {
    echo -n "Starting SecretSafe Backend on port 8051... "
    if [ -f "$BACKEND_PID_FILE" ] && kill -0 $(cat "$BACKEND_PID_FILE") 2>/dev/null; then
        echo "Already running (PID: $(cat "$BACKEND_PID_FILE"))"
        return
    fi

    # Compile the Go backend if binary does not exist or if code has changed
    REBUILD=false
    if [ ! -f "$BACKEND_DIR/secretsafe" ]; then
        REBUILD=true
    else
        # Rebuild if any Go source file is newer than the binary
        if [ -n "$(find "$BACKEND_DIR" -name "*.go" -newer "$BACKEND_DIR/secretsafe" -print -quit 2>/dev/null)" ]; then
            REBUILD=true
        fi
    fi

    if [ "$REBUILD" = true ]; then
        echo -e "\nBuilding Linux backend binary..."
        (cd "$BACKEND_DIR" && export GO111MODULE=on && go build -o secretsafe)
        if [ $? -ne 0 ]; then
            echo "Error: Failed to compile backend"
            return
        fi
    fi

    # Start backend daemon
    cd "$BACKEND_DIR"
    ./secretsafe > "$BACKEND_LOG" 2>&1 &
    BACKEND_PID=$!
    cd ..

    echo $BACKEND_PID > "$BACKEND_PID_FILE"
    echo "Started (PID: $BACKEND_PID)"
}

start_frontend() {
    echo -n "Starting SecretSafe Frontend on port 8050... "
    if [ -f "$FRONTEND_PID_FILE" ] && kill -0 $(cat "$FRONTEND_PID_FILE") 2>/dev/null; then
        echo "Already running (PID: $(cat "$FRONTEND_PID_FILE"))"
        return
    fi

    # Start frontend Vite server
    cd "$FRONTEND_DIR"
    npm run dev > "$FRONTEND_LOG" 2>&1 &
    FRONTEND_PID=$!
    cd ..

    echo $FRONTEND_PID > "$FRONTEND_PID_FILE"
    echo "Started (PID: $FRONTEND_PID)"
}

stop_backend() {
    echo -n "Stopping SecretSafe Backend... "
    if [ -f "$BACKEND_PID_FILE" ]; then
        PID=$(cat "$BACKEND_PID_FILE")
        if kill -0 $PID 2>/dev/null; then
            kill $PID
            # Wait for shutdown
            for i in {1..5}; do
                if ! kill -0 $PID 2>/dev/null; then
                    break
                fi
                sleep 0.5
            done
            # Force kill if still alive after grace period
            if kill -0 $PID 2>/dev/null; then
                kill -9 $PID
            fi
            echo "Stopped"
        else
            echo "Not running"
        fi
        rm -f "$BACKEND_PID_FILE"
    else
        echo "No PID file found"
    fi
}

stop_frontend() {
    echo -n "Stopping SecretSafe Frontend... "
    if [ -f "$FRONTEND_PID_FILE" ]; then
        PID=$(cat "$FRONTEND_PID_FILE")
        if kill -0 $PID 2>/dev/null; then
            # Kill npm child processes (Vite server)
            pkill -P $PID 2>/dev/null
            kill $PID
            echo "Stopped"
        else
            echo "Not running"
        fi
        rm -f "$FRONTEND_PID_FILE"
    else
        echo "No PID file found"
    fi
}

status() {
    # Check Backend
    if [ -f "$BACKEND_PID_FILE" ] && kill -0 $(cat "$BACKEND_PID_FILE") 2>/dev/null; then
        echo "Backend:  RUNNING (PID: $(cat "$BACKEND_PID_FILE"))"
    else
        echo "Backend:  STOPPED"
    fi

    # Check Frontend
    if [ -f "$FRONTEND_PID_FILE" ] && kill -0 $(cat "$FRONTEND_PID_FILE") 2>/dev/null; then
        echo "Frontend: RUNNING (PID: $(cat "$FRONTEND_PID_FILE"))"
    else
        echo "Frontend: STOPPED"
    fi
}

reinstall() {
    echo "========================================================="
    echo "  WARNING: This will delete ALL database data, certificates,"
    echo "  and configuration files. You will need to setup again. "
    echo "========================================================="
    read -p "Are you sure you want to proceed? (y/N): " CONFIRM
    CONFIRM=$(echo "$CONFIRM" | tr '[:upper:]' '[:lower:]')
    if [ "$CONFIRM" != "y" ] && [ "$CONFIRM" != "yes" ]; then
        echo "Reinstallation cancelled."
        return
    fi

    echo "Stopping current services..."
    stop_backend
    stop_frontend

    echo "Cleaning up configuration and database files..."
    rm -f .env
    rm -f frontend/src/config.js
    rm -f backend/secretsafe.db
    rm -f backend/secretsafe
    rm -f backend/cert.pem
    rm -f backend/key.pem

    echo ""
    echo "========================================================="
    echo "  Starting Setup Wizard... "
    echo "========================================================="
    if [ -f "./setup.sh" ]; then
        bash ./setup.sh
    else
        echo "Error: setup.sh not found!"
        return
    fi

    # Reload variables from new .env
    if [ -f .env ]; then
        export $(grep -v '^#' .env | tr -d '\r' | xargs)
    fi

    echo ""
    echo "Rebuilding and restarting services..."
    start_backend
    start_frontend
}

case "$1" in
    start)
        start_backend
        start_frontend
        ;;
    stop)
        stop_backend
        stop_frontend
        ;;
    restart)
        stop_backend
        stop_frontend
        sleep 1
        start_backend
        start_frontend
        ;;
    status)
        status
        ;;
    reinstall)
        reinstall
        ;;
    *)
        echo "Usage: $0 {start|stop|restart|status|reinstall}"
        exit 1
        ;;
esac

exit 0
