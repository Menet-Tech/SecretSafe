package db

import (
	"database/sql"
	"log"
	"os"
	"path/filepath"

	_ "github.com/glebarez/go-sqlite"
)

var DB *sql.DB

// InitDB opens the database connection and creates the necessary tables
func InitDB(dbPath string) error {
	// Ensure parent directory exists
	dir := filepath.Dir(dbPath)
	if err := os.MkdirAll(dir, 0755); err != nil {
		return err
	}

	var err error
	// Open using pure Go sqlite driver
	DB, err = sql.Open("sqlite", dbPath)
	if err != nil {
		return err
	}

	if err = DB.Ping(); err != nil {
		return err
	}

	// Create tables if they do not exist
	schema := `
	CREATE TABLE IF NOT EXISTS users (
		id INTEGER PRIMARY KEY AUTOINCREMENT,
		username TEXT UNIQUE NOT NULL,
		password_hash TEXT NOT NULL,
		role TEXT NOT NULL, -- 'admin' or 'user'
		created_at DATETIME DEFAULT CURRENT_TIMESTAMP
	);

	CREATE TABLE IF NOT EXISTS credentials (
		id INTEGER PRIMARY KEY AUTOINCREMENT,
		user_id INTEGER NOT NULL,
		name TEXT NOT NULL,
		username TEXT NOT NULL,
		encrypted_password TEXT NOT NULL,
		nonce TEXT NOT NULL,
		address TEXT,
		created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
		FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
	);

	CREATE TABLE IF NOT EXISTS approval_requests (
		id TEXT PRIMARY KEY,
		user_id INTEGER NOT NULL,
		credential_id INTEGER NOT NULL,
		requester_host TEXT NOT NULL,
		status TEXT NOT NULL, -- 'pending', 'approved', 'rejected', 'expired'
		created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
		FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE,
		FOREIGN KEY(credential_id) REFERENCES credentials(id) ON DELETE CASCADE
	);

	CREATE TABLE IF NOT EXISTS api_keys (
		id INTEGER PRIMARY KEY AUTOINCREMENT,
		user_id INTEGER NOT NULL,
		name TEXT NOT NULL,
		key_hash TEXT UNIQUE NOT NULL,
		prefix TEXT NOT NULL,
		created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
		FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
	);

	CREATE TABLE IF NOT EXISTS api_key_credentials (
		api_key_id INTEGER NOT NULL,
		credential_id INTEGER NOT NULL,
		PRIMARY KEY(api_key_id, credential_id),
		FOREIGN KEY(api_key_id) REFERENCES api_keys(id) ON DELETE CASCADE,
		FOREIGN KEY(credential_id) REFERENCES credentials(id) ON DELETE CASCADE
	);
	`

	_, err = DB.Exec(schema)
	if err != nil {
		return err
	}

	log.Printf("Database initialized at %s\n", dbPath)
	return nil
}
