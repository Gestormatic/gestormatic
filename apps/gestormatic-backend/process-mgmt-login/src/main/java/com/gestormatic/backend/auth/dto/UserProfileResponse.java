package com.gestormatic.backend.auth.dto;


import java.util.List;

public class UserProfileResponse {
    private Long userId;
    private String tenantId;
    private String authUid;
    private String email;
    private String displayName;
    private List<String> roles;

    public UserProfileResponse(Long userId,
                               String tenantId,
                               String authUid,
                               String email,
                               String displayName,
                               List<String> roles) {
        this.userId = userId;
        this.tenantId = tenantId;
        this.authUid = authUid;
        this.email = email;
        this.displayName = displayName;
        this.roles = roles;
    }

    public Long getUserId() {
        return userId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getAuthUid() {
        return authUid;
    }

    public String getEmail() {
        return email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public List<String> getRoles() {
        return roles;
    }
}
