package main

import (
	"log"
	"net/http"
	"os"

	"secretsafe/db"
	"secretsafe/handlers"
	"secretsafe/middleware"
	"secretsafe/security"
)

func main() {
	// Initialize configurations from env
	port := os.Getenv("PORT")
	if port == "" {
		port = "8051"
	}

	masterKey := os.Getenv("MASTER_KEY")
	security.SetMasterKey(masterKey)

	// Initialize SQLite Database
	dbPath := "./secretsafe.db"
	if err := db.InitDB(dbPath); err != nil {
		log.Fatalf("Failed to initialize database: %v", err)
	}

	// Seed first administrator if tables are empty
	seedAdmin()

	// Launch WebSocket Broadcast Hub
	go handlers.GlobalHub.Run()

	// Set up router with HTTP standard multiplexer
	mux := http.NewServeMux()

	// Public routes
	mux.HandleFunc("POST /api/auth/login", handlers.Login)

	// Authenticated routes (wrapped in AuthMiddleware)
	mux.Handle("GET /api/auth/me", middleware.AuthMiddleware(http.HandlerFunc(handlers.Me)))
	mux.Handle("GET /api/credentials", middleware.AuthMiddleware(http.HandlerFunc(handlers.ListCredentials)))
	mux.Handle("POST /api/credentials", middleware.AuthMiddleware(http.HandlerFunc(handlers.AddCredential)))
	mux.Handle("PUT /api/credentials/{id}", middleware.AuthMiddleware(http.HandlerFunc(handlers.UpdateCredential)))
	mux.Handle("DELETE /api/credentials/{id}", middleware.AuthMiddleware(http.HandlerFunc(handlers.DeleteCredential)))
	mux.Handle("GET /api/credentials/{id}/retrieve", middleware.AuthMiddleware(http.HandlerFunc(handlers.RetrieveCredential)))

	// API Key management routes (wrapped in AuthMiddleware)
	mux.Handle("GET /api/apikeys", middleware.AuthMiddleware(http.HandlerFunc(handlers.ListAPIKeys)))
	mux.Handle("POST /api/apikeys", middleware.AuthMiddleware(http.HandlerFunc(handlers.CreateAPIKey)))
	mux.Handle("PUT /api/apikeys/{id}", middleware.AuthMiddleware(http.HandlerFunc(handlers.UpdateAPIKey)))
	mux.Handle("DELETE /api/apikeys/{id}", middleware.AuthMiddleware(http.HandlerFunc(handlers.DeleteAPIKey)))
	mux.Handle("POST /api/apikeys/{id}/rotate", middleware.AuthMiddleware(http.HandlerFunc(handlers.RotateAPIKey)))

	// WebSocket handler for the Android client
	mux.Handle("GET /api/ws", middleware.AuthMiddleware(http.HandlerFunc(handlers.ServeWS)))

	// Admin-only management endpoints (wrapped in Auth and Admin middlewares)
	mux.Handle("POST /api/auth/register", middleware.AuthMiddleware(middleware.AdminMiddleware(http.HandlerFunc(handlers.Register))))
	mux.Handle("GET /api/auth/users", middleware.AuthMiddleware(middleware.AdminMiddleware(http.HandlerFunc(handlers.ListUsers))))

	// Wrap entire router with CORS support
	corsHandler := corsMiddleware(mux)

	// Ensure TLS Certificates exist (generate self-signed certs automatically if missing)
	certFile := "cert.pem"
	keyFile := "key.pem"
	if err := security.EnsureTLSCertificates(certFile, keyFile); err != nil {
		log.Fatalf("Failed to generate/read TLS certificates: %v", err)
	}

	log.Printf("SecretSafe HTTPS Server starting on port %s...\n", port)
	if err := http.ListenAndServeTLS(":"+port, certFile, keyFile, corsHandler); err != nil {
		log.Fatalf("Server shutdown failed: %v", err)
	}
}

// corsMiddleware injects required headers for local and web client cross-origin traffic
func corsMiddleware(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Access-Control-Allow-Origin", "*")
		w.Header().Set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
		w.Header().Set("Access-Control-Allow-Headers", "Content-Type, Authorization, X-API-Key")

		if r.Method == http.MethodOptions {
			w.WriteHeader(http.StatusOK)
			return
		}

		next.ServeHTTP(w, r)
	})
}

// seedAdmin sets up the initial default credentials if no users exist
func seedAdmin() {
	var count int
	err := db.DB.QueryRow("SELECT COUNT(*) FROM users").Scan(&count)
	if err != nil {
		log.Fatalf("Failed to scan users for seeding: %v", err)
	}

	if count == 0 {
		adminUser := os.Getenv("ADMIN_USER")
		if adminUser == "" {
			adminUser = "admin"
		}
		adminPass := os.Getenv("ADMIN_PASS")
		if adminPass == "" {
			adminPass = "admin123"
		}

		hashedPassword, err := security.HashPassword(adminPass)
		if err != nil {
			log.Fatalf("Failed to hash default admin password: %v", err)
		}

		_, err = db.DB.Exec("INSERT INTO users (username, password_hash, role) VALUES (?, ?, ?)", adminUser, hashedPassword, "admin")
		if err != nil {
			log.Fatalf("Failed to seed default admin: %v", err)
		}

		log.Printf("[Database] Seeded default admin account: %s / %s\n", adminUser, adminPass)
	}
}
