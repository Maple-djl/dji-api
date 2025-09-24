-- 测试PostgreSQL版本的SQL文件
-- 这个文件用于测试cloud_sample_postgresql.sql是否正确

-- 创建测试数据库
CREATE DATABASE test_dji_cloud;

-- 连接到测试数据库
\c test_dji_cloud;

-- 设置字符集
SET client_encoding = 'UTF8';

-- 创建扩展
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 测试表创建
\echo '开始测试表创建...'

-- 测试设备飞行区域表
DROP TABLE IF EXISTS device_flight_area CASCADE;
CREATE TABLE device_flight_area (
    id SERIAL PRIMARY KEY,
    device_sn VARCHAR(64) NOT NULL,
    workspace_id VARCHAR(64) NOT NULL,
    file_id VARCHAR(64) NOT NULL,
    sync_status VARCHAR(32) NOT NULL,
    sync_code INTEGER NOT NULL DEFAULT 0,
    create_time BIGINT NOT NULL,
    update_time BIGINT NOT NULL
);
\echo 'device_flight_area表创建成功'

-- 测试日志文件表
DROP TABLE IF EXISTS logs_file CASCADE;
CREATE TABLE logs_file (
    id BIGSERIAL PRIMARY KEY,
    file_id VARCHAR(45) NOT NULL UNIQUE DEFAULT '',
    name VARCHAR(100) NOT NULL DEFAULT '',
    size BIGINT NOT NULL DEFAULT 0,
    logs_id VARCHAR(45) NOT NULL DEFAULT '',
    device_sn VARCHAR(45) NOT NULL DEFAULT '',
    fingerprint VARCHAR(64) NOT NULL DEFAULT '',
    object_key VARCHAR(1000) NOT NULL DEFAULT '',
    status BOOLEAN NOT NULL,
    create_time BIGINT NOT NULL,
    update_time BIGINT NOT NULL
);
\echo 'logs_file表创建成功'

-- 测试媒体文件表
DROP TABLE IF EXISTS media_file CASCADE;
CREATE TABLE media_file (
    id SERIAL PRIMARY KEY,
    file_id VARCHAR(64) NOT NULL UNIQUE DEFAULT '',
    file_name VARCHAR(100) NOT NULL DEFAULT '',
    file_path VARCHAR(1000) NOT NULL DEFAULT '',
    workspace_id VARCHAR(64) NOT NULL DEFAULT '',
    fingerprint VARCHAR(64) DEFAULT '',
    tinny_fingerprint VARCHAR(100) DEFAULT '',
    object_key VARCHAR(1000) NOT NULL DEFAULT '',
    sub_file_type INTEGER DEFAULT NULL,
    is_original BOOLEAN NOT NULL,
    drone VARCHAR(32) NOT NULL DEFAULT 'undefined',
    payload VARCHAR(32) NOT NULL DEFAULT 'undefined',
    job_id VARCHAR(64) DEFAULT '',
    create_time BIGINT NOT NULL,
    update_time BIGINT NOT NULL
);
\echo 'media_file表创建成功'

-- 测试工作空间表
DROP TABLE IF EXISTS manage_workspace CASCADE;
CREATE TABLE manage_workspace (
    id SERIAL PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL UNIQUE DEFAULT '',
    workspace_name VARCHAR(64) NOT NULL DEFAULT '',
    workspace_desc VARCHAR(100) NOT NULL DEFAULT '',
    platform_name VARCHAR(64) NOT NULL DEFAULT '',
    create_time BIGINT NOT NULL,
    update_time BIGINT NOT NULL,
    bind_code VARCHAR(32) NOT NULL UNIQUE DEFAULT ''
);
\echo 'manage_workspace表创建成功'

-- 测试设备表
DROP TABLE IF EXISTS manage_device CASCADE;
CREATE TABLE manage_device (
    id SERIAL PRIMARY KEY,
    device_sn VARCHAR(32) NOT NULL UNIQUE DEFAULT '',
    device_name VARCHAR(64) NOT NULL DEFAULT 'undefined',
    user_id VARCHAR(64) DEFAULT '',
    nickname VARCHAR(64) NOT NULL,
    workspace_id VARCHAR(64) DEFAULT '',
    device_type INTEGER NOT NULL DEFAULT -1,
    sub_type INTEGER NOT NULL DEFAULT -1,
    domain INTEGER NOT NULL DEFAULT -1,
    firmware_version VARCHAR(32) DEFAULT '',
    compatible_status BOOLEAN NOT NULL DEFAULT true,
    version VARCHAR(32) DEFAULT '',
    device_index VARCHAR(32) DEFAULT '',
    child_sn VARCHAR(32) DEFAULT '',
    create_time BIGINT NOT NULL,
    update_time BIGINT NOT NULL,
    bound_time BIGINT DEFAULT NULL,
    bound_status BOOLEAN NOT NULL DEFAULT false,
    login_time BIGINT DEFAULT NULL,
    device_desc VARCHAR(100) DEFAULT '',
    url_normal VARCHAR(200) DEFAULT '',
    url_select VARCHAR(200) DEFAULT ''
);
\echo 'manage_device表创建成功'

-- 插入测试数据
INSERT INTO manage_workspace (id, workspace_id, workspace_name, workspace_desc, platform_name, create_time, update_time, bind_code)
VALUES (1,'test-workspace-id','Test Workspace','Test Description','Test Platform',1634898410751,1634898410751,'test-code');

INSERT INTO manage_device (id, device_sn, device_name, nickname, workspace_id, device_type, bound_status, create_time, update_time)
VALUES (1,'test-device-sn','Test Device','Test Nickname','test-workspace-id',0,true,1634898410751,1634898410751);

INSERT INTO logs_file (id, file_id, name, size, device_sn, status, create_time, update_time)
VALUES (1,'test-log-file-id','test.log',1024,'test-device-sn',true,1634898410751,1634898410751);

INSERT INTO media_file (id, file_id, file_name, workspace_id, is_original, create_time, update_time)
VALUES (1,'test-media-file-id','test.jpg','test-workspace-id',true,1634898410751,1634898410751);

\echo '测试数据插入成功'

-- 测试统计视图
\echo '开始测试统计视图...'

-- 测试设备统计视图
CREATE OR REPLACE VIEW v_device_statistics AS
SELECT 
    w.workspace_name,
    COUNT(md.id) as total_devices,
    COUNT(CASE WHEN md.bound_status = true THEN 1 END) as bound_devices,
    COUNT(CASE WHEN md.bound_status = false THEN 1 END) as unbound_devices,
    COUNT(CASE WHEN md.device_type = 0 THEN 1 END) as drone_count,
    COUNT(CASE WHEN md.device_type = 1 THEN 1 END) as payload_count,
    COUNT(CASE WHEN md.device_type = 2 THEN 1 END) as remote_control_count,
    COUNT(CASE WHEN md.device_type = 3 THEN 1 END) as dock_count
FROM manage_workspace w
LEFT JOIN manage_device md ON w.workspace_id = md.workspace_id
GROUP BY w.workspace_id, w.workspace_name;

\echo 'v_device_statistics视图创建成功'

-- 测试文件统计视图
CREATE OR REPLACE VIEW v_file_statistics AS
SELECT 
    w.workspace_name,
    COUNT(mf.id) as total_media_files,
    0 as total_media_size,
    0 as total_wayline_files,
    COUNT(lf.id) as total_log_files,
    SUM(lf.size) as total_log_size
FROM manage_workspace w
LEFT JOIN media_file mf ON w.workspace_id = mf.workspace_id
LEFT JOIN logs_file lf ON EXISTS (
    SELECT 1 FROM manage_device md 
    WHERE md.workspace_id = w.workspace_id 
    AND md.device_sn = lf.device_sn
)
GROUP BY w.workspace_id, w.workspace_name;

\echo 'v_file_statistics视图创建成功'

-- 测试查询
\echo '开始测试查询...'

SELECT '设备统计测试:' as test_name;
SELECT * FROM v_device_statistics;

SELECT '文件统计测试:' as test_name;
SELECT * FROM v_file_statistics;

\echo '所有测试完成！'

-- 清理测试数据库
\c postgres;
DROP DATABASE test_dji_cloud;
\echo '测试数据库已清理'
