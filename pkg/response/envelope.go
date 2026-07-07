// Package response defines unified API response envelopes.
package response

// Envelope is the standard API response wrapper.
type Envelope struct {
	Code      int         `json:"code"`
	Message   string      `json:"message"`
	Data      interface{} `json:"data,omitempty"`
	RequestID string      `json:"request_id,omitempty"`
	Details   interface{} `json:"details,omitempty"`
}

// Page holds paginated list metadata.
type Page struct {
	Items    interface{} `json:"items"`
	Total    int64       `json:"total"`
	Page     int         `json:"page"`
	PageSize int         `json:"page_size"`
}

// OK returns a success envelope.
func OK(data interface{}) Envelope {
	return Envelope{Code: 0, Message: "ok", Data: data}
}

// Error returns an error envelope.
func Error(code int, message string, details interface{}) Envelope {
	return Envelope{Code: code, Message: message, Details: details}
}
