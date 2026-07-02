package com.omits.social_api.health;

import java.util.Map;

public record HealthResponse(String status, Map<String, String> checks) {
}
