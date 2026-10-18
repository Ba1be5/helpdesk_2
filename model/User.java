package model;

import java.time.LocalDateTime;

public class User {
    private Long id;
    private String name;
    private String email;
    private UserRole role;
    private Long departmentId;
    private boolean active = true;
    private LocalDateTime createdAt;

    public User() {
    }

    public User(String name, String email, UserRole role, Long departmentId) {
        this.name = name;
        this.email = email;
        this.role = role;
        this.departmentId = departmentId;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public UserRole getRole() { return role; }
    public void setRole(UserRole role) { this.role = role; }
    public Long getDepartmentId() { return departmentId; }
    public void setDepartmentId(Long departmentId) { this.departmentId = departmentId; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    @Override
    public String toString() {
        return "User{id=" + id + ", name='" + name + "', email='" + email
                + "', role=" + role + "}";
    }
}