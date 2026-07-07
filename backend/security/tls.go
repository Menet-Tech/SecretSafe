package security

import (
	"crypto/rand"
	"crypto/rsa"
	"crypto/x509"
	"crypto/x509/pkix"
	"encoding/pem"
	"math/big"
	"net"
	"os"
	"time"
)

// EnsureTLSCertificates checks if cert.pem and key.pem exist; if not, generates them automatically.
// If they do exist, it parses the cert to verify if it is older than 30 days, automatically regenerating it to rotate monthly.
func EnsureTLSCertificates(certPath, keyPath string) error {
	_, errCert := os.Stat(certPath)
	_, errKey := os.Stat(keyPath)
	if errCert == nil && errKey == nil {
		// Parse existing certificate to verify age/expiration
		certBytes, err := os.ReadFile(certPath)
		if err == nil {
			block, _ := pem.Decode(certBytes)
			if block != nil && block.Type == "CERTIFICATE" {
				cert, err := x509.ParseCertificate(block.Bytes)
				if err == nil {
					// Rotate monthly: if created > 30 days ago, or expiring in < 30 days
					if time.Now().After(cert.NotBefore.Add(30 * 24 * time.Hour)) || time.Now().After(cert.NotAfter.Add(-30 * 24 * time.Hour)) {
						// Remove files to trigger regeneration below
						_ = os.Remove(certPath)
						_ = os.Remove(keyPath)
					} else {
						return nil // Fresh certificates already exist
					}
				}
			}
		}
	}

	// Generate a 2048-bit RSA key pair
	priv, err := rsa.GenerateKey(rand.Reader, 2048)
	if err != nil {
		return err
	}

	// Define validity period (1 year)
	notBefore := time.Now()
	notAfter := notBefore.Add(365 * 24 * time.Hour)

	serialNumberLimit := new(big.Int).Lsh(big.NewInt(1), 128)
	serialNumber, err := rand.Int(rand.Reader, serialNumberLimit)
	if err != nil {
		return err
	}

	template := x509.Certificate{
		SerialNumber: serialNumber,
		Subject: pkix.Name{
			Organization: []string{"SecretSafe Security"},
		},
		NotBefore:             notBefore,
		NotAfter:              notAfter,
		KeyUsage:              x509.KeyUsageKeyEncipherment | x509.KeyUsageDigitalSignature,
		ExtKeyUsage:           []x509.ExtKeyUsage{x509.ExtKeyUsageServerAuth},
		BasicConstraintsValid: true,
	}

	// Attach common local IP bindings and DNS hostnames
	template.IPAddresses = append(template.IPAddresses, net.ParseIP("127.0.0.1"), net.ParseIP("0.0.0.0"))
	template.DNSNames = append(template.DNSNames, "localhost")

	// Create self-signed certificate DER bytes
	derBytes, err := x509.CreateCertificate(rand.Reader, &template, &template, &priv.PublicKey, priv)
	if err != nil {
		return err
	}

	// Encode and save Certificate file
	certOut, err := os.Create(certPath)
	if err != nil {
		return err
	}
	defer certOut.Close()
	if err := pem.Encode(certOut, &pem.Block{Type: "CERTIFICATE", Bytes: derBytes}); err != nil {
		return err
	}

	// Encode and save Private Key file
	keyOut, err := os.OpenFile(keyPath, os.O_WRONLY|os.O_CREATE|os.O_TRUNC, 0600)
	if err != nil {
		return err
	}
	defer keyOut.Close()

	privBytes, err := x509.MarshalPKCS8PrivateKey(priv)
	if err != nil {
		return err
	}
	if err := pem.Encode(keyOut, &pem.Block{Type: "PRIVATE KEY", Bytes: privBytes}); err != nil {
		return err
	}

	return nil
}
