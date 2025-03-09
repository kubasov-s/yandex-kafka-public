package com.example.module2.model;

import java.util.List;
import java.util.Set;

public record UserSet(Set<String> users) {
    public UserSet {
        if (users == null) {
            throw new IllegalArgumentException("users should not be null");
        }
    }
    public UserSet(String[] users) {
        this(Set.copyOf(List.of(users == null ? new String[0] : users)));
    }
}
