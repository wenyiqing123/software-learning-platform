package edu.wfust.learning.platform.auth;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("auth_refresh_token")
public class RefreshTokenEntity {
    @TableId(type = IdType.AUTO) private Long id;
    private Long userId;
    private String tokenHash;
    private java.time.LocalDateTime expiresAt;
    private java.time.LocalDateTime revokedAt;
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getTokenHash() { return tokenHash; }
    public void setTokenHash(String tokenHash) { this.tokenHash = tokenHash; }
    public java.time.LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(java.time.LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
    public java.time.LocalDateTime getRevokedAt() { return revokedAt; }
    public void setRevokedAt(java.time.LocalDateTime revokedAt) { this.revokedAt = revokedAt; }
}
