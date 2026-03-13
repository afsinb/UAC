package com.blacklight.uac.domain.common;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Base Domain Entity - All domain entities inherit from this
 * Implements DDD principles
 */
public abstract class DomainEntity {
    protected final String id;
    protected final LocalDateTime createdAt;
    protected LocalDateTime updatedAt;
    
    protected DomainEntity() {
        this.id = UUID.randomUUID().toString();
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
    
    protected DomainEntity(String id) {
        this.id = id;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
    
    public String getId() {
        return id;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
    
    protected void markUpdated() {
        this.updatedAt = LocalDateTime.now();
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DomainEntity)) return false;
        DomainEntity that = (DomainEntity) o;
        return id.equals(that.id);
    }
    
    @Override
    public int hashCode() {
        return id.hashCode();
    }
}

