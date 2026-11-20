package edu.wfust.learning.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import edu.wfust.learning.platform.entity.AiExperimentEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AiExperimentMapper extends BaseMapper<AiExperimentEntity> {
}
