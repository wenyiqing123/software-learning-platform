package edu.wfust.learning.platform.studyplan;

import com.baomidou.mybatisplus.spring.MybatisSqlSessionFactoryBean;
import edu.wfust.learning.platform.mapper.StudyPlanMapper;
import org.apache.ibatis.session.SqlSessionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import javax.sql.DataSource;
import static org.assertj.core.api.Assertions.assertThat;

@SpringJUnitConfig(StudyPlanMapperIntegrationTest.Config.class)
class StudyPlanMapperIntegrationTest {
    @Configuration(proxyBeanMethods=false)
    @MapperScan(basePackageClasses=StudyPlanMapper.class, annotationClass=org.apache.ibatis.annotations.Mapper.class)
    static class Config {
        @Bean DataSource dataSource(){var ds=new JdbcDataSource();ds.setURL("jdbc:h2:mem:study-plan-mapper;MODE=MySQL;DB_CLOSE_DELAY=-1");return ds;}
        @Bean SqlSessionFactory sqlSessionFactory(DataSource ds)throws Exception{
            var factory=new MybatisSqlSessionFactoryBean();factory.setDataSource(ds);
            var config=new com.baomidou.mybatisplus.core.MybatisConfiguration();config.setMapUnderscoreToCamelCase(true);factory.setConfiguration(config);
            return factory.getObject();
        }
    }
    @Autowired DataSource dataSource;
    @Autowired StudyPlanMapper mapper;

    @BeforeEach void schema(){
        String sql="""
            DROP ALL OBJECTS;
            CREATE TABLE course(id BIGINT PRIMARY KEY,name VARCHAR(120),enabled BOOLEAN);
            CREATE TABLE course_chapter(id BIGINT PRIMARY KEY,course_id BIGINT,name VARCHAR(120),sort_order INT);
            CREATE TABLE knowledge_point(id BIGINT PRIMARY KEY,course_id BIGINT,enabled BOOLEAN);
            CREATE TABLE course_study_section(id BIGINT PRIMARY KEY,course_id BIGINT,chapter_id BIGINT,knowledge_point_id BIGINT,title VARCHAR(160),sort_order INT);
            CREATE TABLE course_study_progress(user_id BIGINT,section_id BIGINT,completed BOOLEAN);
            INSERT INTO course VALUES(1,'软件工程',TRUE);
            INSERT INTO course_chapter VALUES(2,1,'需求分析',1);
            INSERT INTO knowledge_point VALUES(3,1,TRUE);
            INSERT INTO course_study_section VALUES(4,1,2,3,'学习需求分析',1);
            """;
        new ResourceDatabasePopulator(new org.springframework.core.io.ByteArrayResource(sql.getBytes(java.nio.charset.StandardCharsets.UTF_8))).execute(dataSource);
    }

    @Test void mapsReadingCandidateColumnsToTheCandidateResult(){
        var result=mapper.readingCandidates(7L);
        assertThat(result).singleElement().satisfies(candidate->{
            assertThat(candidate.getTargetId()).isEqualTo(4L);
            assertThat(candidate.getCourseId()).isEqualTo(1L);
            assertThat(candidate.getTitle()).isEqualTo("学习需求分析");
        });
    }
}
