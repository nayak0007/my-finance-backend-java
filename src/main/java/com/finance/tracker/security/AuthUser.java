package com.finance.tracker.security;

import java.util.UUID;

public record AuthUser(UUID id, String email, String role) {
}
