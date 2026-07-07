package server

import (
	"encoding/json"
	"net/http"

	"github.com/jone-code/SentinelHub/pkg/response"
)

// Server is the HTTP server for the remote service.
type Server struct {
	mux *http.ServeMux
}

// New creates a new Server with default routes.
func New() *Server {
	s := &Server{mux: http.NewServeMux()}
	s.mux.HandleFunc("GET /health", s.handleHealth)
	s.mux.HandleFunc("GET /ready", s.handleReady)
	return s
}

// ListenAndServe starts the HTTP server.
func (s *Server) ListenAndServe(addr string) error {
	return http.ListenAndServe(addr, s.mux)
}

func (s *Server) handleHealth(w http.ResponseWriter, _ *http.Request) {
	writeJSON(w, response.OK(map[string]string{
		"service": "remote",
		"status":  "healthy",
	}))
}

func (s *Server) handleReady(w http.ResponseWriter, _ *http.Request) {
	writeJSON(w, response.OK(map[string]string{"status": "ready"}))
}

func writeJSON(w http.ResponseWriter, v interface{}) {
	w.Header().Set("Content-Type", "application/json")
	_ = json.NewEncoder(w).Encode(v)
}
