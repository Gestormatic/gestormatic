package com.gestormatic.backend.admin.dto;


import java.util.List;

public class SetClaimsResponse {
    private String uid;
    private String tenantId;
    private List<String> roles;

    public SetClaimsResponse(String uid, String tenantId, List<String> roles) {
        this.uid = uid;
        this.tenantId = tenantId;
        this.roles = roles;
    }

    public String getUid() {
        return uid;
    }

    public String getTenantId() {
        return tenantId;
    }

    public List<String> getRoles() {
        return roles;
    }
}
