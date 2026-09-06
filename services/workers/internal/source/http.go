package source

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
)

// maxResponseBytes caps how much of any source response is read into memory. The largest
// realistic payload is a 100-item GitHub search page.
const maxResponseBytes = 8 << 20

// UserAgent identifies the radar to the sources it polls. Several of them (GitHub in
// particular) reject or throttle requests that do not send one.
const UserAgent = "social-tool-trend-radar/1.0 (+https://github.com/olmits/social-tool)"

// GetJSON performs a GET against url and decodes the JSON body into out. Non-2xx responses
// become errors carrying the status and a short prefix of the body, which is what makes a
// rate-limit or auth failure legible in the logs rather than surfacing as a decode error.
func GetJSON(ctx context.Context, client *http.Client, url string, out any) error {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, url, nil)
	if err != nil {
		return fmt.Errorf("build GET %s: %w", url, err)
	}
	req.Header.Set("Accept", "application/json")
	req.Header.Set("User-Agent", UserAgent)

	resp, err := client.Do(req)
	if err != nil {
		return fmt.Errorf("GET %s: %w", url, err)
	}
	defer resp.Body.Close()

	body, err := io.ReadAll(io.LimitReader(resp.Body, maxResponseBytes))
	if err != nil {
		return fmt.Errorf("read GET %s response: %w", url, err)
	}

	if resp.StatusCode < 200 || resp.StatusCode > 299 {
		return fmt.Errorf("GET %s: status %d: %s", url, resp.StatusCode, snippet(body))
	}
	if err := json.Unmarshal(body, out); err != nil {
		return fmt.Errorf("decode GET %s response: %w", url, err)
	}
	return nil
}

// snippet trims a response body down to something loggable.
func snippet(body []byte) string {
	const limit = 200
	if len(body) > limit {
		return string(body[:limit]) + "..."
	}
	return string(body)
}
