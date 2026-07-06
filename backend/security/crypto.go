package security

import (
	"crypto/aes"
	"crypto/cipher"
	"crypto/rand"
	"encoding/base64"
	"io"

	"golang.org/x/crypto/bcrypt"
)

// MasterKey is used for AES-256-GCM. 32 bytes.
var MasterKey = []byte("secretsafe-super-secret-key-32b!") // Fallback key

// SetMasterKey updates the MasterKey from environment variables or custom configuration
func SetMasterKey(keyStr string) {
	if len(keyStr) > 0 {
		newKey := make([]byte, 32)
		copy(newKey, []byte(keyStr))
		MasterKey = newKey
	}
}

// HashPassword hashes a plain text password with bcrypt
func HashPassword(password string) (string, error) {
	bytes, err := bcrypt.GenerateFromPassword([]byte(password), bcrypt.DefaultCost)
	if err != nil {
		return "", err
	}
	return string(bytes), nil
}

// CheckPasswordHash compares a password hash with a plain text password
func CheckPasswordHash(password, hash string) bool {
	err := bcrypt.CompareHashAndPassword([]byte(hash), []byte(password))
	return err == nil
}

// Encrypt encrypts a plaintext string using AES-GCM, returning the base64 ciphertext and base64 nonce.
func Encrypt(plainText string) (string, string, error) {
	block, err := aes.NewCipher(MasterKey)
	if err != nil {
		return "", "", err
	}

	aesGCM, err := cipher.NewGCM(block)
	if err != nil {
		return "", "", err
	}

	nonce := make([]byte, aesGCM.NonceSize())
	if _, err = io.ReadFull(rand.Reader, nonce); err != nil {
		return "", "", err
	}

	cipherText := aesGCM.Seal(nil, nonce, []byte(plainText), nil)

	return base64.StdEncoding.EncodeToString(cipherText), base64.StdEncoding.EncodeToString(nonce), nil
}

// Decrypt decrypts base64 ciphertext and nonce using AES-GCM, returning the original plaintext.
func Decrypt(cipherTextBase64, nonceBase64 string) (string, error) {
	cipherText, err := base64.StdEncoding.DecodeString(cipherTextBase64)
	if err != nil {
		return "", err
	}

	nonce, err := base64.StdEncoding.DecodeString(nonceBase64)
	if err != nil {
		return "", err
	}

	block, err := aes.NewCipher(MasterKey)
	if err != nil {
		return "", err
	}

	aesGCM, err := cipher.NewGCM(block)
	if err != nil {
		return "", err
	}

	plainText, err := aesGCM.Open(nil, nonce, cipherText, nil)
	if err != nil {
		return "", err
	}

	return string(plainText), nil
}
