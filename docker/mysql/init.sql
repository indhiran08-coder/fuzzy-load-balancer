-- =============================================================================
-- init.sql — Optional MySQL initialization script
-- Runs once when the MySQL container is first created.
-- =============================================================================

-- Ensure UTF-8 character set for full Unicode support
ALTER DATABASE fuzzy_lb_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- Grant all privileges to the application user (already created by Docker env vars)
GRANT ALL PRIVILEGES ON fuzzy_lb_db.* TO 'fuzzy'@'%';
FLUSH PRIVILEGES;
