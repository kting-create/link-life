package com.linklife.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.linklife.auth.entity.BindingCode;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface BindingCodeMapper extends BaseMapper<BindingCode> {
    @Select("SELECT * FROM binding_code WHERE code = #{code} AND used_at IS NULL ORDER BY id DESC LIMIT 1")
    BindingCode selectByCode(@Param("code") String code);
}
