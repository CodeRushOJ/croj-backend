package com.zephyr.croj.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zephyr.croj.model.entity.TestBundle;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface TestBundleMapper extends BaseMapper<TestBundle> {
    @Select("SELECT * FROM t_test_bundle WHERE problem_version_id=#{versionId}")
    TestBundle findByProblemVersionId(@Param("versionId") long versionId);
}
