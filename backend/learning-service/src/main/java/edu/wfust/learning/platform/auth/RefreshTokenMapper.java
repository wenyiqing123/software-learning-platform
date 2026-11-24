package edu.wfust.learning.platform.auth;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import java.time.LocalDateTime;

@Mapper
public interface RefreshTokenMapper extends BaseMapper<RefreshTokenEntity> {
    @Select("SELECT * FROM auth_refresh_token WHERE token_hash = #{tokenHash} AND revoked_at IS NULL AND expires_at > CURRENT_TIMESTAMP(6) LIMIT 1 FOR UPDATE")
    RefreshTokenEntity findActive(String tokenHash);

    @Update("UPDATE auth_refresh_token SET revoked_at = #{revokedAt} WHERE user_id = #{userId} AND revoked_at IS NULL")
    int revokeAllActiveByUserId(@Param("userId") Long userId, @Param("revokedAt") LocalDateTime revokedAt);
}
