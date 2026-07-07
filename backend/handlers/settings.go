package handlers

import (
	"crypto/tls"
	"io"
	"net/http"
	"os"
)

// UploadTLS receives cert.pem and key.pem files, validates them as a valid TLS keypair,
// and saves them to disk. The Go backend's dynamic GetCertificate hook will automatically
// reload and apply them on the next incoming connection without server downtime.
func UploadTLS(w http.ResponseWriter, r *http.Request) {
	// Parse multipart form up to 10MB
	err := r.ParseMultipartForm(10 << 20)
	if err != nil {
		http.Error(w, "Failed to parse form: "+err.Error(), http.StatusBadRequest)
		return
	}

	certFile, _, err := r.FormFile("certificate")
	if err != nil {
		http.Error(w, "Certificate file is required ('certificate' key)", http.StatusBadRequest)
		return
	}
	defer certFile.Close()

	keyFile, _, err := r.FormFile("privateKey")
	if err != nil {
		http.Error(w, "Private Key file is required ('privateKey' key)", http.StatusBadRequest)
		return
	}
	defer keyFile.Close()

	certBytes, err := io.ReadAll(certFile)
	if err != nil {
		http.Error(w, "Failed to read certificate: "+err.Error(), http.StatusInternalServerError)
		return
	}

	keyBytes, err := io.ReadAll(keyFile)
	if err != nil {
		http.Error(w, "Failed to read private key: "+err.Error(), http.StatusInternalServerError)
		return
	}

	// Verify that they form a valid TLS keypair
	_, err = tls.X509KeyPair(certBytes, keyBytes)
	if err != nil {
		http.Error(w, "Invalid TLS Certificate or Private Key. The private key must match the public key of the certificate.", http.StatusBadRequest)
		return
	}

	// Save to disk
	err = os.WriteFile("cert.pem", certBytes, 0600)
	if err != nil {
		http.Error(w, "Failed to save cert.pem: "+err.Error(), http.StatusInternalServerError)
		return
	}

	err = os.WriteFile("key.pem", keyBytes, 0600)
	if err != nil {
		http.Error(w, "Failed to save key.pem: "+err.Error(), http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusOK)
	w.Write([]byte(`{"status":"success","message":"TLS certificate and private key updated successfully"}`))
}
