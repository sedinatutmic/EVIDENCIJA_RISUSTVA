package org.example.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class User {
    private long id;
    private String fullName;
    private String email;
    private String qrValue;
    private boolean active;
    private LocalDateTime createdAt;

    // new fields
    private LocalDate birthDate;
    private String address;
    private String contact;
    private Role role; // Praktikant or Volonter
    private String profileImagePath;
    private String cvFilePath;

    public User() {}

    public User(long id, String fullName, String email, String qrValue, boolean active, LocalDateTime createdAt) {
        this.id = id;
        this.fullName = fullName;
        this.email = email;
        this.qrValue = qrValue;
        this.active = active;
        this.createdAt = createdAt;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getQrValue() { return qrValue; }
    public void setQrValue(String qrValue) { this.qrValue = qrValue; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    // new fields getters/setters
    public LocalDate getBirthDate() { return birthDate; }
    public void setBirthDate(LocalDate birthDate) { this.birthDate = birthDate; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getContact() { return contact; }
    public void setContact(String contact) { this.contact = contact; }

    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }

    public String getProfileImagePath() { return profileImagePath; }
    public void setProfileImagePath(String profileImagePath) { this.profileImagePath = profileImagePath; }

    public String getCvFilePath() { return cvFilePath; }
    public void setCvFilePath(String cvFilePath) { this.cvFilePath = cvFilePath; }
}
