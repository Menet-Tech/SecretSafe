package handlers

import (
	"database/sql"
	"encoding/json"
	"log"
	"net/http"
	"strconv"
	"strings"

	"secretsafe/db"
	"secretsafe/middleware"
	"secretsafe/security"
)

// CredentialResponse represents credential info without the sensitive password
type CredentialResponse struct {
	ID        int    `json:"id"`
	Name      string `json:"name"`
	Username  string `json:"username"`
	Address   string `json:"address,omitempty"`
	CreatedAt string `json:"created_at"`
}

// AddCredentialRequest schema for adding or updating credentials
type AddCredentialRequest struct {
	Name     string `json:"name"`
	Username string `json:"username"`
	Password string `json:"password"`
	Address  string `json:"address"`
}

// DecryptedCredentialResponse schema containing the decrypted password payload
type DecryptedCredentialResponse struct {
	ID       int    `json:"id"`
	Name     string `json:"name"`
	Username string `json:"username"`
	Password string `json:"password"`
	Address  string `json:"address"`
}

// ListCredentials lists all credentials for the current user (passwords omitted)
func ListCredentials(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}

	userID := r.Context().Value(middleware.UserIDKey).(int)
	authMethod, _ := r.Context().Value(middleware.AuthMethodKey).(string)

	var rows *sql.Rows
	var err error

	if authMethod == "apikey" {
		apiKeyID, _ := r.Context().Value(middleware.ApiKeyIDKey).(int)
		rows, err = db.DB.Query(`
			SELECT c.id, c.name, c.username, c.address, c.created_at 
			FROM credentials c 
			JOIN api_key_credentials akc ON c.id = akc.credential_id 
			WHERE akc.api_key_id = ?`, 
			apiKeyID,
		)
	} else {
		rows, err = db.DB.Query("SELECT id, name, username, address, created_at FROM credentials WHERE user_id = ?", userID)
	}

	if err != nil {
		http.Error(w, "Database error", http.StatusInternalServerError)
		return
	}
	defer rows.Close()

	list := []CredentialResponse{}
	for rows.Next() {
		var c CredentialResponse
		var addr sql.NullString
		if err := rows.Scan(&c.ID, &c.Name, &c.Username, &addr, &c.CreatedAt); err != nil {
			http.Error(w, "Error parsing results", http.StatusInternalServerError)
			return
		}
		if addr.Valid {
			c.Address = addr.String
		}
		list = append(list, c)
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(list)
}

// AddCredential encrypts and stores a new credential
func AddCredential(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}

	userID := r.Context().Value(middleware.UserIDKey).(int)

	var req AddCredentialRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "Invalid input", http.StatusBadRequest)
		return
	}

	if req.Name == "" || req.Username == "" || req.Password == "" {
		http.Error(w, "Missing required fields", http.StatusBadRequest)
		return
	}

	encryptedPassword, nonce, err := security.Encrypt(req.Password)
	if err != nil {
		http.Error(w, "Encryption failed", http.StatusInternalServerError)
		return
	}

	_, err = db.DB.Exec(
		"INSERT INTO credentials (user_id, name, username, encrypted_password, nonce, address) VALUES (?, ?, ?, ?, ?, ?)",
		userID, req.Name, req.Username, encryptedPassword, nonce, req.Address,
	)
	if err != nil {
		http.Error(w, "Database error", http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusCreated)
	json.NewEncoder(w).Encode(map[string]string{"message": "Credential added successfully"})
}

// UpdateCredential updates an existing credential (password is optional)
func UpdateCredential(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPut {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}

	userID := r.Context().Value(middleware.UserIDKey).(int)

	idStr := r.PathValue("id")
	credID, err := strconv.Atoi(idStr)
	if err != nil {
		http.Error(w, "Invalid credential ID", http.StatusBadRequest)
		return
	}

	var req AddCredentialRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "Invalid input", http.StatusBadRequest)
		return
	}

	// Verify owner owns this credential
	var count int
	err = db.DB.QueryRow("SELECT COUNT(*) FROM credentials WHERE id = ? AND user_id = ?", credID, userID).Scan(&count)
	if err != nil || count == 0 {
		http.Error(w, "Credential not found or unauthorized", http.StatusNotFound)
		return
	}

	if req.Password != "" {
		encryptedPassword, nonce, err := security.Encrypt(req.Password)
		if err != nil {
			http.Error(w, "Encryption failed", http.StatusInternalServerError)
			return
		}
		_, err = db.DB.Exec(
			"UPDATE credentials SET name = ?, username = ?, encrypted_password = ?, nonce = ?, address = ? WHERE id = ? AND user_id = ?",
			req.Name, req.Username, encryptedPassword, nonce, req.Address, credID, userID,
		)
	} else {
		_, err = db.DB.Exec(
			"UPDATE credentials SET name = ?, username = ?, address = ? WHERE id = ? AND user_id = ?",
			req.Name, req.Username, req.Address, credID, userID,
		)
	}

	if err != nil {
		http.Error(w, "Database error", http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]string{"message": "Credential updated successfully"})
}

// DeleteCredential deletes a credential
func DeleteCredential(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodDelete {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}

	userID := r.Context().Value(middleware.UserIDKey).(int)

	idStr := r.PathValue("id")
	credID, err := strconv.Atoi(idStr)
	if err != nil {
		http.Error(w, "Invalid credential ID", http.StatusBadRequest)
		return
	}

	_, err = db.DB.Exec("DELETE FROM credentials WHERE id = ? AND user_id = ?", credID, userID)
	if err != nil {
		http.Error(w, "Database error", http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]string{"message": "Credential deleted successfully"})
}

// RetrieveCredential triggers WebSocket approval notification to mobile, blocks, and decrypts the password on success
func RetrieveCredential(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}

	userID := r.Context().Value(middleware.UserIDKey).(int)

	idStr := r.PathValue("id")
	credID, err := strconv.Atoi(idStr)
	if err != nil {
		http.Error(w, "Invalid credential ID", http.StatusBadRequest)
		return
	}

	var name, credUsername, encryptedPassword, nonce string
	var address sql.NullString
	var ownerID int
	err = db.DB.QueryRow(
		"SELECT user_id, name, username, encrypted_password, nonce, address FROM credentials WHERE id = ?",
		credID,
	).Scan(&ownerID, &name, &credUsername, &encryptedPassword, &nonce, &address)

	if err != nil {
		if err == sql.ErrNoRows {
			http.Error(w, "Credential not found", http.StatusNotFound)
			return
		}
		http.Error(w, "Database error", http.StatusInternalServerError)
		return
	}

	// Verify owner matches target (prevent cross-user credential leaks)
	if ownerID != userID {
		http.Error(w, "Credential not found", http.StatusNotFound)
		return
	}

	// If authenticated via API Key, verify permission ACL mapping
	authMethod, _ := r.Context().Value(middleware.AuthMethodKey).(string)
	if authMethod == "apikey" {
		apiKeyID, _ := r.Context().Value(middleware.ApiKeyIDKey).(int)
		var count int
		err = db.DB.QueryRow(
			"SELECT COUNT(*) FROM api_key_credentials WHERE api_key_id = ? AND credential_id = ?",
			apiKeyID, credID,
		).Scan(&count)
		if err != nil || count == 0 {
			http.Error(w, "Forbidden: API Key not authorized for this credential", http.StatusForbidden)
			return
		}
	}

	// Capture requester host details
	requesterHost := r.URL.Query().Get("host")
	if requesterHost == "" {
		requesterHost = r.RemoteAddr
	}

	// Trigger real-time approval prompt via WebSocket (blocks execution thread)
	requestID, approved, err := RequestApproval(userID, name, credUsername, requesterHost)

	status := "pending"
	if err != nil {
		status = "expired"
	} else if approved {
		status = "approved"
	} else {
		status = "rejected"
	}

	// Log transaction to DB
	_, dbErr := db.DB.Exec(
		"INSERT INTO approval_requests (id, user_id, credential_id, requester_host, status) VALUES (?, ?, ?, ?, ?)",
		requestID, userID, credID, requesterHost, status,
	)
	if dbErr != nil {
		log.Printf("[Database] Failed to log approval request: %v", dbErr)
	}

	if err != nil {
		if strings.Contains(err.Error(), "no active Android app connected") {
			http.Error(w, "Access Denied: No active Android app connection found for authorization.", http.StatusPreconditionFailed)
		} else {
			http.Error(w, "Authorization Timeout: "+err.Error(), http.StatusRequestTimeout)
		}
		return
	}

	if !approved {
		http.Error(w, "Access Denied: Request rejected on Android device.", http.StatusForbidden)
		return
	}

	// User approved, decrypt password
	decryptedPassword, err := security.Decrypt(encryptedPassword, nonce)
	if err != nil {
		http.Error(w, "Decryption failed", http.StatusInternalServerError)
		return
	}

	addrStr := ""
	if address.Valid {
		addrStr = address.String
	}

	resp := DecryptedCredentialResponse{
		ID:       credID,
		Name:     name,
		Username: credUsername,
		Password: decryptedPassword,
		Address:  addrStr,
	}

	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(resp)
}
