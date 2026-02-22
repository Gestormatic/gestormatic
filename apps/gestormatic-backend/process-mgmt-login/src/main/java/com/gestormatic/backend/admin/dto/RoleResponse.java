package com.gestormatic.backend.admin.dto;


public class RoleResponse {
    private String name;

    public RoleResponse(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
