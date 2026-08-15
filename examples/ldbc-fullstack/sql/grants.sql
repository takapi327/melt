-- world.sql creates the `world` database itself, so the `ldbc` user created by
-- MYSQL_USER has no privileges on it. Grant them explicitly.
GRANT ALL PRIVILEGES ON `world`.* TO 'ldbc'@'%';
FLUSH PRIVILEGES;
