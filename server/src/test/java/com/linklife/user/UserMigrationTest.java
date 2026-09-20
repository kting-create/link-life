package com.linklife.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.linklife.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class UserMigrationTest extends IntegrationTestBase {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void userHasTokenVersionColumn() {
        Integer cnt = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns "
                        + "WHERE table_schema='linklife' AND table_name='user' "
                        + "AND column_name='token_version'", Integer.class);
        assertEquals(1, cnt);
    }

    @Test
    void unionidIndexIsUnique() {
        Integer rows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.statistics "
                        + "WHERE table_schema='linklife' AND table_name='user' "
                        + "AND index_name='uk_unionid'", Integer.class);
        assertTrue(rows != null && rows > 0, "索引 uk_unionid 必须存在");
        Boolean unique = jdbc.queryForObject(
                "SELECT COUNT(*) = 0 FROM information_schema.statistics "
                        + "WHERE table_schema='linklife' AND table_name='user' "
                        + "AND index_name='uk_unionid' AND non_unique = 1", Boolean.class);
        assertTrue(unique);
    }

    @Test
    void bindingCodeIndexRenamed() {
        Integer idx = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.statistics "
                        + "WHERE table_schema='linklife' AND table_name='binding_code' "
                        + "AND index_name='idx_code'", Integer.class);
        Integer old = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.statistics "
                        + "WHERE table_schema='linklife' AND table_name='binding_code' "
                        + "AND index_name='uk_code'", Integer.class);
        assertTrue(idx > 0);
        assertEquals(0, old);
    }
}
