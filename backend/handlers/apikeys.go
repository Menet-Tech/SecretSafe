package handlers

import (
	"crypto/rand"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"net/http"
	"strconv"

	"secretsafe/db"
	"secretsafe/middleware"
)

type APIKeyResponse struct {
	ID            int      `json:"id"`
	Name          string   `json:"name"`
	Prefix        string   `json:"prefix"`
	CreatedAt     string   `json:"created_at"`
	CredentialIDs []int    `json:"credential_ids"`
}

type CreateAPIKeyRequest struct {
	Name          string `json:"name"`
	CredentialIDs []int  `json:"credential_ids"`
}

type CreateAPIKeyResponse struct {
	Key    string `json:"key"`
	Prefix string `json:"prefix"`
	Name   string `json:"name"`
}

// ListAPIKeys retrieves all API Keys for the authenticated user, along with credential mappings
func ListAPIKeys(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}

	userID := r.Context().Value(middleware.UserIDKey).(int)

	rows, err := db.DB.Query("SELECT id, name, prefix, created_at FROM api_keys WHERE user_id = ?", userID)
	if err != nil {
		http.Error(w, "Database error", http.StatusInternalServerError)
		return
	}
	defer rows.Close()

	list := []APIKeyResponse{}
	for rows.Next() {
		var k APIKeyResponse
		if err := rows.Scan(&k.ID, &k.Name, &k.Prefix, &k.CreatedAt); err != nil {
			http.Error(w, "Scan error", http.StatusInternalServerError)
			return
		}

		// Fetch permitted credentials mapping
		k.CredentialIDs = []int{}
		credRows, err := db.DB.Query("SELECT credential_id FROM api_key_credentials WHERE api_key_id = ?", k.ID)
		if err == nil {
			for credRows.Next() {
				var credID int
				if err := credRows.Scan(&credID); err == nil {
					k.CredentialIDs = append(k.CredentialIDs, credID)
				}
			}
			credRows.Close()
		}

		list = append(list, k)
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(list)
}

// CreateAPIKey generates a secure random API key and stores its SHA-256 hash in database
func CreateAPIKey(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}

	userID := r.Context().Value(middleware.UserIDKey).(int)

	var req CreateAPIKeyRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "Invalid input", http.StatusBadRequest)
		return
	}

	if req.Name == "" {
		http.Error(w, "API Key name is required", http.StatusBadRequest)
		return
	}

	// Generate secure random API key (32 bytes = 64 hex characters)
	keyBytes := make([]byte, 20)
	if _, err := rand.Read(keyBytes); err != nil {
		http.Error(w, "Failed to generate key", http.StatusInternalServerError)
		return
	}
	rawKey := "ss_key_" + hex.EncodeToString(keyBytes)
	prefix := rawKey[:11] // "ss_key_xxxx" shown for identification

	// Store SHA-256 hash of key to prevent plain text database leaks
	hasher := sha256.New()
	hasher.Write([]byte(rawKey))
	hashStr := hex.EncodeToString(hasher.Sum(nil))

	tx, err := db.DB.Begin()
	if err != nil {
		http.Error(w, "Database error", http.StatusInternalServerError)
		return
	}
	defer tx.Rollback()

	res, err := tx.Exec("INSERT INTO api_keys (user_id, name, key_hash, prefix) VALUES (?, ?, ?, ?)", userID, req.Name, hashStr, prefix)
	if err != nil {
		http.Error(w, "Failed to store API Key", http.StatusInternalServerError)
		return
	}

	keyID64, err := res.LastInsertId()
	if err != nil {
		http.Error(w, "Failed to get insert ID", http.StatusInternalServerError)
		return
	}
	keyID := int(keyID64)

	// Save many-to-many credential associations
	for _, credID := range req.CredentialIDs {
		// Confirm user owns target credential before saving mapping (prevent ACL bypasses)
		var count int
		err := tx.QueryRow("SELECT COUNT(*) FROM credentials WHERE id = ? AND user_id = ?", credID, userID).Scan(&count)
		if err == nil && count > 0 {
			_, _ = tx.Exec("INSERT INTO api_key_credentials (api_key_id, credential_id) VALUES (?, ?)", keyID, credID)
		}
	}

	if err := tx.Commit(); err != nil {
		http.Error(w, "Transaction failed", http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusCreated)
	json.NewEncoder(w).Encode(CreateAPIKeyResponse{
		Key:    rawKey,
		Prefix: prefix,
		Name:   req.Name,
	})
}

// DeleteAPIKey revokes the API key
func DeleteAPIKey(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodDelete {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}

	userID := r.Context().Value(middleware.UserIDKey).(int)

	idStr := r.PathValue("id")
	keyID, err := strconv.Atoi(idStr)
	if err != nil {
		http.Error(w, "Invalid API Key ID", http.StatusBadRequest)
		return
	}

	_, err = db.DB.Exec("DELETE FROM api_keys WHERE id = ? AND user_id = ?", keyID, userID)
	if err != nil {
		http.Error(w, "Database error", http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]string{"message": "API Key revoked successfully"})
}

type UpdateAPIKeyRequest struct {
	Name          string `json:"name"`
	CredentialIDs []int  `json:"credential_ids"`
}

// UpdateAPIKey modifies the name and/or credential permission mapping for the key
func UpdateAPIKey(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPut {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}

	userID := r.Context().Value(middleware.UserIDKey).(int)

	idStr := r.PathValue("id")
	keyID, err := strconv.Atoi(idStr)
	if err != nil {
		http.Error(w, "Invalid API Key ID", http.StatusBadRequest)
		return
	}

	var req UpdateAPIKeyRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "Invalid input", http.StatusBadRequest)
		return
	}

	var exists int
	err = db.DB.QueryRow("SELECT COUNT(*) FROM api_keys WHERE id = ? AND user_id = ?", keyID, userID).Scan(&exists)
	if err != nil || exists == 0 {
		http.Error(w, "API Key not found", http.StatusNotFound)
		return
	}

	tx, err := db.DB.Begin()
	if err != nil {
		http.Error(w, "Database error", http.StatusInternalServerError)
		return
	}
	defer tx.Rollback()

	if req.Name != "" {
		_, err = tx.Exec("UPDATE api_keys SET name = ? WHERE id = ?", req.Name, keyID)
		if err != nil {
			http.Error(w, "Failed to update name", http.StatusInternalServerError)
			return
		}
	}

	// Clear out existing mappings and insert new ones
	_, err = tx.Exec("DELETE FROM api_key_credentials WHERE api_key_id = ?", keyID)
	if err != nil {
		http.Error(w, "Failed to reset mapping", http.StatusInternalServerError)
		return
	}

	for _, credID := range req.CredentialIDs {
		var count int
		err := tx.QueryRow("SELECT COUNT(*) FROM credentials WHERE id = ? AND user_id = ?", credID, userID).Scan(&count)
		if err == nil && count > 0 {
			_, _ = tx.Exec("INSERT INTO api_key_credentials (api_key_id, credential_id) VALUES (?, ?)", keyID, credID)
		}
	}

	if err := tx.Commit(); err != nil {
		http.Error(w, "Transaction failed", http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]string{"message": "API Key updated successfully"})
}

// RotateAPIKey invalidates the existing key and generates a new raw key
func RotateAPIKey(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}

	userID := r.Context().Value(middleware.UserIDKey).(int)

	idStr := r.PathValue("id")
	keyID, err := strconv.Atoi(idStr)
	if err != nil {
		http.Error(w, "Invalid API Key ID", http.StatusBadRequest)
		return
	}

	var name string
	err = db.DB.QueryRow("SELECT name FROM api_keys WHERE id = ? AND user_id = ?", keyID, userID).Scan(&name)
	if err != nil {
		http.Error(w, "API Key not found", http.StatusNotFound)
		return
	}

	// Generate new key
	keyBytes := make([]byte, 20)
	if _, err := rand.Read(keyBytes); err != nil {
		http.Error(w, "Failed to generate key", http.StatusInternalServerError)
		return
	}
	rawKey := "ss_key_" + hex.EncodeToString(keyBytes)
	prefix := rawKey[:11]

	hasher := sha256.New()
	hasher.Write([]byte(rawKey))
	hashStr := hex.EncodeToString(hasher.Sum(nil))

	_, err = db.DB.Exec("UPDATE api_keys SET key_hash = ?, prefix = ? WHERE id = ? AND user_id = ?", hashStr, prefix, keyID, userID)
	if err != nil {
		http.Error(w, "Database error", http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(CreateAPIKeyResponse{
		Key:    rawKey,
		Prefix: prefix,
		Name:   name,
	})
}
