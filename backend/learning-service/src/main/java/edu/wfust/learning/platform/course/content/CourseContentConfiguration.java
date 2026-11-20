package edu.wfust.learning.platform.course.content;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@MapperScan(basePackageClasses = CourseStudyMapper.class, annotationClass = Mapper.class)
public class CourseContentConfiguration { }
