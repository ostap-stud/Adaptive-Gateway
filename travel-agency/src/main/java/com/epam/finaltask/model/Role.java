package com.epam.finaltask.model;

import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public enum Role {
    ADMIN, MANAGER, USER;

    private final Set<Permission> permissions = Arrays.stream(Permission.values())
            .filter(permission -> permission.name().contains(this.name()))
            .collect(Collectors.toSet());

    public List<SimpleGrantedAuthority> getAuthorities() {
        return permissions.stream()
                .map(permission -> new SimpleGrantedAuthority(permission.name()))
                .toList();
    }
}
