package edu.wfust.learning.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import edu.wfust.learning.platform.entity.SysUserEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SysUserMapper extends BaseMapper<SysUserEntity> {
    @Select("SELECT * FROM sys_user WHERE username = #{username} LIMIT 1")
    SysUserEntity findByUsername(String username);
}
