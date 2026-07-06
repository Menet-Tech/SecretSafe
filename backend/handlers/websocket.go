package handlers

import (
	"crypto/rand"
	"encoding/json"
	"errors"
	"fmt"
	"log"
	"net/http"
	"sync"
	"time"

	"secretsafe/middleware"

	"github.com/gorilla/websocket"
)

var upgrader = websocket.Upgrader{
	ReadBufferSize:  1024,
	WriteBufferSize: 1024,
	CheckOrigin: func(r *http.Request) bool {
		return true // Allow all origins for dev environment
	},
}

// Client represents a connected websocket client (Android app)
type Client struct {
	UserID int
	Conn   *websocket.Conn
	Send   chan []byte
}

// WSMessage represents the WebSocket JSON communication structure
type WSMessage struct {
	Type           string `json:"type"` // "approval_request", "approval_response", "ping", "pong", "error"
	RequestID      string `json:"request_id,omitempty"`
	CredentialName string `json:"credential_name,omitempty"`
	Username       string `json:"username,omitempty"`
	Host           string `json:"host,omitempty"`
	Approved       bool   `json:"approved,omitempty"`
}

// Hub manages active connections and broadcasts notifications
type Hub struct {
	clients          map[int]map[*Client]bool // UserID -> set of client connections
	register         chan *Client
	unregister       chan *Client
	pendingApprovals map[string]chan bool
	mu               sync.RWMutex
}

// GlobalHub instance
var GlobalHub = &Hub{
	clients:          make(map[int]map[*Client]bool),
	register:         make(chan *Client),
	unregister:       make(chan *Client),
	pendingApprovals: make(map[string]chan bool),
}

// Run executes the hub operations in the background
func (h *Hub) Run() {
	for {
		select {
		case client := <-h.register:
			h.mu.Lock()
			if _, ok := h.clients[client.UserID]; !ok {
				h.clients[client.UserID] = make(map[*Client]bool)
			}
			h.clients[client.UserID][client] = true
			h.mu.Unlock()
			log.Printf("[WebSocket] Registered client for UserID: %d", client.UserID)

		case client := <-h.unregister:
			h.mu.Lock()
			if users, ok := h.clients[client.UserID]; ok {
				if _, exists := users[client]; exists {
					delete(users, client)
					close(client.Send)
					client.Conn.Close()
					log.Printf("[WebSocket] Unregistered client for UserID: %d", client.UserID)
				}
				if len(users) == 0 {
					delete(h.clients, client.UserID)
				}
			}
			h.mu.Unlock()
		}
	}
}

// ReadPump listens for incoming messages from a client connection
func (c *Client) ReadPump() {
	defer func() {
		GlobalHub.unregister <- c
	}()

	c.Conn.SetReadLimit(512)
	c.Conn.SetReadDeadline(time.Now().Add(60 * time.Second))
	c.Conn.SetPongHandler(func(string) error {
		c.Conn.SetReadDeadline(time.Now().Add(60 * time.Second))
		return nil
	})

	for {
		_, message, err := c.Conn.ReadMessage()
		if err != nil {
			if websocket.IsUnexpectedCloseError(err, websocket.CloseGoingAway, websocket.CloseAbnormalClosure) {
				log.Printf("[WebSocket] Read error: %v", err)
			}
			break
		}

		var msg WSMessage
		if err := json.Unmarshal(message, &msg); err != nil {
			log.Printf("[WebSocket] Failed to unmarshal message: %v", err)
			continue
		}

		switch msg.Type {
		case "approval_response":
			GlobalHub.mu.Lock()
			ch, ok := GlobalHub.pendingApprovals[msg.RequestID]
			if ok {
				ch <- msg.Approved
				delete(GlobalHub.pendingApprovals, msg.RequestID)
				close(ch)
			}
			GlobalHub.mu.Unlock()
		case "ping":
			c.Send <- []byte(`{"type":"pong"}`)
		}
	}
}

// WritePump pushes message queue to the active connection
func (c *Client) WritePump() {
	ticker := time.NewTicker(54 * time.Second)
	defer func() {
		ticker.Stop()
		c.Conn.Close()
	}()

	for {
		select {
		case message, ok := <-c.Send:
			c.Conn.SetWriteDeadline(time.Now().Add(10 * time.Second))
			if !ok {
				c.Conn.WriteMessage(websocket.CloseMessage, []byte{})
				return
			}

			w, err := c.Conn.NextWriter(websocket.TextMessage)
			if err != nil {
				return
			}
			w.Write(message)

			// Flush other buffered messages in send queue
			n := len(c.Send)
			for i := 0; i < n; i++ {
				w.Write(<-c.Send)
			}

			if err := w.Close(); err != nil {
				return
			}

		case <-ticker.C:
			c.Conn.SetWriteDeadline(time.Now().Add(10 * time.Second))
			if err := c.Conn.WriteMessage(websocket.PingMessage, nil); err != nil {
				return
			}
		}
	}
}

// ServeWS handles WebSocket connection upgrade and client registration
func ServeWS(w http.ResponseWriter, r *http.Request) {
	userID, ok := r.Context().Value(middleware.UserIDKey).(int)
	if !ok {
		http.Error(w, "Unauthorized", http.StatusUnauthorized)
		return
	}

	conn, err := upgrader.Upgrade(w, r, nil)
	if err != nil {
		log.Printf("[WebSocket] Upgrade error: %v", err)
		return
	}

	client := &Client{
		UserID: userID,
		Conn:   conn,
		Send:   make(chan []byte, 256),
	}

	GlobalHub.register <- client

	go client.WritePump()
	go client.ReadPump()
}

func uuidGen() string {
	b := make([]byte, 16)
	_, _ = rand.Read(b)
	return fmt.Sprintf("%x-%x-%x-%x-%x", b[0:4], b[4:6], b[6:8], b[8:10], b[10:])
}

// RequestApproval triggers approval check and blocks until user approves/rejects or 30s timeout expires
func RequestApproval(userID int, credentialName, username, requesterHost string) (string, bool, error) {
	requestID := uuidGen()

	// Check if user has active mobile socket connection
	GlobalHub.mu.RLock()
	clients, ok := GlobalHub.clients[userID]
	if !ok || len(clients) == 0 {
		GlobalHub.mu.RUnlock()
		return requestID, false, errors.New("no active Android app connected")
	}
	GlobalHub.mu.RUnlock()

	ch := make(chan bool, 1)

	GlobalHub.mu.Lock()
	GlobalHub.pendingApprovals[requestID] = ch
	GlobalHub.mu.Unlock()

	msg := WSMessage{
		Type:           "approval_request",
		RequestID:      requestID,
		CredentialName: credentialName,
		Username:       username,
		Host:           requesterHost,
	}

	bytes, err := json.Marshal(msg)
	if err != nil {
		GlobalHub.mu.Lock()
		delete(GlobalHub.pendingApprovals, requestID)
		GlobalHub.mu.Unlock()
		return requestID, false, err
	}

	// Broadcast to all active client sockets of this user
	GlobalHub.mu.RLock()
	for client := range GlobalHub.clients[userID] {
		select {
		case client.Send <- bytes:
		default:
			// Write channel saturated; client will be cleaned up
		}
	}
	GlobalHub.mu.RUnlock()

	// Await client approval response or timeout limit
	select {
	case approved := <-ch:
		return requestID, approved, nil
	case <-time.After(30 * time.Second):
		GlobalHub.mu.Lock()
		if _, exists := GlobalHub.pendingApprovals[requestID]; exists {
			delete(GlobalHub.pendingApprovals, requestID)
			close(ch)
		}
		GlobalHub.mu.Unlock()
		return requestID, false, errors.New("request timed out waiting for approval")
	}
}
