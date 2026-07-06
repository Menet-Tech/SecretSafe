package middleware

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"net/http"
	"strings"

	"secretsafe/db"

	"github.com/golang-jwt/jwt/v5"
)

var JWTKey = []byte("secretsafe-super-jwt-secret-key-123!") // default jwt key

type ContextKey string

const (
	UserIDKey     ContextKey = "userID"
	UsernameKey   ContextKey = "username"
	UserRoleKey   ContextKey = "role"
	AuthMethodKey ContextKey = "authMethod" // "jwt" or "apikey"
	ApiKeyIDKey   ContextKey = "apiKeyID"   // int
)

type Claims struct {
	UserID   int    `json:"user_id"`
	Username string `json:"username"`
	Role     string `json:"role"`
	jwt.RegisteredClaims
}

// AuthMiddleware extracts JWT token or API Key and populates context
func AuthMiddleware(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		// 1. Check for API Key authentication header
		apiKey := r.Header.Get("X-API-Key")
		if apiKey != "" {
			hasher := sha256.New()
			hasher.Write([]byte(apiKey))
			hashStr := hex.EncodeToString(hasher.Sum(nil))

			var keyID int
			var userID int
			var keyName string
			err := db.DB.QueryRow("SELECT id, user_id, name FROM api_keys WHERE key_hash = ?", hashStr).Scan(&keyID, &userID, &keyName)
			if err == nil {
				// Valid API Key
				ctx := context.WithValue(r.Context(), UserIDKey, userID)
				ctx = context.WithValue(ctx, UsernameKey, "API-Key: "+keyName)
				ctx = context.WithValue(ctx, UserRoleKey, "apikey")
				ctx = context.WithValue(ctx, AuthMethodKey, "apikey")
				ctx = context.WithValue(ctx, ApiKeyIDKey, keyID)
				next.ServeHTTP(w, r.WithContext(ctx))
				return
			}
			// Invalid API Key - reject immediately
			http.Error(w, "Unauthorized: Invalid API Key", http.StatusUnauthorized)
			return
		}

		// 2. Fall back to standard JWT Token validation
		tokenStr := ""
		authHeader := r.Header.Get("Authorization")
		if authHeader != "" {
			parts := strings.Split(authHeader, " ")
			if len(parts) == 2 && parts[0] == "Bearer" {
				tokenStr = parts[1]
			}
		}

		if tokenStr == "" {
			tokenStr = r.URL.Query().Get("token")
		}

		if tokenStr == "" {
			http.Error(w, "Unauthorized: Missing Token or API Key", http.StatusUnauthorized)
			return
		}

		claims := &Claims{}
		token, err := jwt.ParseWithClaims(tokenStr, claims, func(token *jwt.Token) (interface{}, error) {
			return JWTKey, nil
		})

		if err != nil || !token.Valid {
			http.Error(w, "Unauthorized: Invalid Token", http.StatusUnauthorized)
			return
		}

		ctx := context.WithValue(r.Context(), UserIDKey, claims.UserID)
		ctx = context.WithValue(ctx, UsernameKey, claims.Username)
		ctx = context.WithValue(ctx, UserRoleKey, claims.Role)
		ctx = context.WithValue(ctx, AuthMethodKey, "jwt")

		next.ServeHTTP(w, r.WithContext(ctx))
	})
}

// AdminMiddleware blocks requests if the authenticated user is not an admin
func AdminMiddleware(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		role, ok := r.Context().Value(UserRoleKey).(string)
		if !ok || role != "admin" {
			http.Error(w, "Forbidden: Admin access required", http.StatusForbidden)
			return
		}
		next.ServeHTTP(w, r)
	})
}
