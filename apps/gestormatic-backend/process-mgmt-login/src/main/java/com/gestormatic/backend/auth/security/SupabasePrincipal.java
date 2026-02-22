package com.gestormatic.backend.auth.security;


import java.io.Serializable;
import java.util.List;

public record SupabasePrincipal(String uid,
                                String email,
                                String name,
                                String tenantId,
                                List<String> roles) implements Serializable {
}
