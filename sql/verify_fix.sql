-- 验证PostgreSQL SQL文件的关键部分
-- 这个脚本验证修复后的统计视图是否正确

-- 测试文件统计视图的修复
-- 原始错误：logs_file表没有workspace_id字段
-- 修复方案：通过device_sn关联到manage_device表

-- 模拟表结构
CREATE TABLE IF NOT EXISTS manage_workspace (
    workspace_id VARCHAR(64) PRIMARY KEY,
    workspace_name VARCHAR(64) NOT NULL
);

CREATE TABLE IF NOT EXISTS manage_device (
    device_sn VARCHAR(32) PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL
);

CREATE TABLE IF NOT EXISTS media_file (
    id SERIAL PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL
);

CREATE TABLE IF NOT EXISTS logs_file (
    id SERIAL PRIMARY KEY,
    device_sn VARCHAR(32) NOT NULL,
    size BIGINT NOT NULL DEFAULT 0
);

-- 插入测试数据
INSERT INTO manage_workspace VALUES ('ws1', 'Workspace 1');
INSERT INTO manage_device VALUES ('device1', 'ws1');
INSERT INTO media_file (workspace_id) VALUES ('ws1');
INSERT INTO logs_file (device_sn, size) VALUES ('device1', 1024);

-- 测试修复后的统计视图
CREATE OR REPLACE VIEW v_file_statistics_test AS
SELECT 
    w.workspace_name,
    COUNT(mf.id) as total_media_files,
    0 as total_media_size, -- media_file表没有size字段
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

-- 测试查询
SELECT * FROM v_file_statistics_test;

-- 清理
DROP VIEW IF EXISTS v_file_statistics_test;
DROP TABLE IF EXISTS logs_file;
DROP TABLE IF EXISTS media_file;
DROP TABLE IF EXISTS manage_device;
DROP TABLE IF EXISTS manage_workspace;
