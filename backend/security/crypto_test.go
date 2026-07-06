package security

import (
	"testing"
)

func TestHashPassword(t *testing.T) {
	password := "my-secure-password"
	hash, err := HashPassword(password)
	if err != nil {
		t.Fatalf("Failed to hash password: %v", err)
	}

	if hash == "" {
		t.Fatal("Hash was empty")
	}

	if !CheckPasswordHash(password, hash) {
		t.Fatal("Hash verification failed for correct password")
	}

	if CheckPasswordHash("wrong-password", hash) {
		t.Fatal("Hash verification succeeded for incorrect password")
	}
}

func TestEncryptDecrypt(t *testing.T) {
	originalText := "secret-message-here-12345!"

	cipherText, nonce, err := Encrypt(originalText)
	if err != nil {
		t.Fatalf("Encryption failed: %v", err)
	}

	if cipherText == "" || nonce == "" {
		t.Fatal("CipherText or Nonce is empty")
	}

	decryptedText, err := Decrypt(cipherText, nonce)
	if err != nil {
		t.Fatalf("Decryption failed: %v", err)
	}

	if decryptedText != originalText {
		t.Fatalf("Decrypted text '%s' does not match original '%s'", decryptedText, originalText)
	}
}
