package com.cityledger.cityledger;

import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class DatabaseFixer implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    public DatabaseFixer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) throws Exception {
        try {
            log.info("Removing old app_users_role_check constraint to allow the new ADMIN role...");
            jdbcTemplate.execute("ALTER TABLE app_users DROP CONSTRAINT IF EXISTS app_users_role_check");
            log.info("Successfully dropped the check constraint! You can now log in as ADMIN.");
        } catch (Exception e) {
            log.error("Note: Failed to drop constraint (it might not exist): {}", e.getMessage());
        }
    }
}
