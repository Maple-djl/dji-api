# DJI Cloud Sample PostgreSQL版本使用说明

## 概述

本文档介绍了从MySQL版本的`cloud_sample.sql`转换而来的PostgreSQL版本数据库，并添加了完整的统计目录功能。

## 主要特性

### 1. 数据库结构转换
- ✅ 将MySQL语法转换为PostgreSQL语法
- ✅ 数据类型适配（如`tinyint(1)` → `BOOLEAN`）
- ✅ 自增主键转换（`AUTO_INCREMENT` → `SERIAL`）
- ✅ 字符集设置（`utf8` → `UTF8`）
- ✅ 引擎设置移除（PostgreSQL不需要）

### 2. 统计目录功能

#### 2.1 统计视图
- **v_device_statistics**: 设备统计视图
  - 总设备数、已绑定设备、未绑定设备
  - 按设备类型分类统计（无人机、载荷、遥控器、机库）

- **v_file_statistics**: 文件统计视图
  - 媒体文件数量和大小
  - 航线文件数量
  - 日志文件数量和大小

- **v_job_statistics**: 任务统计视图
  - 任务状态统计（待执行、进行中、成功、取消、失败）
  - 生成媒体数量统计

- **v_hms_statistics**: HMS消息统计视图
  - 按级别统计（通知、警告、错误）
  - 按模块统计（飞行任务、设备管理、媒体、HMS）

- **v_comprehensive_statistics**: 综合统计视图
  - 整合所有统计信息的综合视图

#### 2.2 统计函数
- **get_workspace_statistics(workspace_id)**: 获取指定工作空间的统计信息
- **get_device_type_statistics(workspace_id)**: 获取设备类型统计
- **generate_statistics_report(workspace_id)**: 生成统计报告

#### 2.3 性能优化
- 创建了关键字段的索引
- 添加了触发器用于实时统计更新
- 优化了查询性能

## 安装和使用

### 1. 安装PostgreSQL
```bash
# Ubuntu/Debian
sudo apt-get install postgresql postgresql-contrib

# CentOS/RHEL
sudo yum install postgresql-server postgresql-contrib

# Windows
# 下载并安装PostgreSQL官方安装包
```

### 2. 创建数据库
```sql
-- 连接到PostgreSQL
psql -U postgres

-- 创建数据库
CREATE DATABASE dji_cloud;

-- 连接到数据库
\c dji_cloud;

-- 执行SQL文件
\i /path/to/cloud_sample_postgresql.sql
```

### 3. 使用统计功能

#### 3.1 查看设备统计
```sql
-- 查看所有工作空间的设备统计
SELECT * FROM v_device_statistics;

-- 查看指定工作空间的设备统计
SELECT * FROM get_workspace_statistics('e3dea0f5-37f2-4d79-ae58-490af3228069');
```

#### 3.2 查看文件统计
```sql
-- 查看文件统计
SELECT * FROM v_file_statistics;

-- 查看媒体文件大小（按GB）
SELECT 
    workspace_name,
    total_media_files,
    ROUND(total_media_size / 1024.0 / 1024.0 / 1024.0, 2) as total_media_size_gb
FROM v_file_statistics;
```

#### 3.3 查看任务统计
```sql
-- 查看任务统计
SELECT * FROM v_job_statistics;

-- 查看任务成功率
SELECT 
    workspace_name,
    total_jobs,
    success_jobs,
    ROUND(success_jobs::numeric / NULLIF(total_jobs, 0) * 100, 2) as success_rate_percent
FROM v_job_statistics;
```

#### 3.4 生成统计报告
```sql
-- 生成全局统计报告
SELECT generate_statistics_report();

-- 生成指定工作空间统计报告
SELECT generate_statistics_report('e3dea0f5-37f2-4d79-ae58-490af3228069');
```

#### 3.5 查看设备类型统计
```sql
-- 查看设备类型分布
SELECT * FROM get_device_type_statistics('e3dea0f5-37f2-4d79-ae58-490af3228069');
```

## 主要差异对比

| 特性 | MySQL版本 | PostgreSQL版本 |
|------|-----------|----------------|
| 数据类型 | `tinyint(1)` | `BOOLEAN` |
| 自增主键 | `AUTO_INCREMENT` | `SERIAL` |
| 字符集 | `utf8` | `UTF8` |
| 引擎 | `ENGINE=InnoDB` | 无（PostgreSQL不需要） |
| 注释 | `COMMENT` | `COMMENT ON` |
| 统计功能 | 无 | 完整的统计视图和函数 |
| 索引优化 | 基础 | 针对统计查询优化 |
| 触发器 | 无 | 实时统计更新 |

## 性能优化建议

### 1. 索引使用
- 已为常用查询字段创建索引
- 根据实际使用情况可添加复合索引

### 2. 查询优化
- 使用统计视图而非直接查询基础表
- 利用PostgreSQL的查询计划器优化

### 3. 定期维护
```sql
-- 更新表统计信息
ANALYZE;

-- 重建索引（如需要）
REINDEX DATABASE dji_cloud;
```

## 扩展功能

### 1. 添加新的统计维度
```sql
-- 示例：添加按时间维度的统计
CREATE OR REPLACE VIEW v_time_statistics AS
SELECT 
    DATE_TRUNC('day', to_timestamp(create_time/1000)) as date,
    COUNT(*) as daily_created_devices
FROM manage_device
GROUP BY DATE_TRUNC('day', to_timestamp(create_time/1000));
```

### 2. 自定义统计函数
```sql
-- 示例：获取设备在线率
CREATE OR REPLACE FUNCTION get_device_online_rate(p_workspace_id VARCHAR(64))
RETURNS DECIMAL AS $$
DECLARE
    total_devices INTEGER;
    online_devices INTEGER;
BEGIN
    SELECT COUNT(*) INTO total_devices
    FROM manage_device 
    WHERE workspace_id = p_workspace_id;
    
    SELECT COUNT(*) INTO online_devices
    FROM manage_device 
    WHERE workspace_id = p_workspace_id 
    AND login_time > (EXTRACT(EPOCH FROM NOW()) * 1000 - 300000); -- 5分钟内登录
    
    RETURN CASE 
        WHEN total_devices > 0 THEN (online_devices::DECIMAL / total_devices * 100)
        ELSE 0 
    END;
END;
$$ LANGUAGE plpgsql;
```

## 故障排除

### 1. 常见问题
- **字符编码问题**: 确保客户端和数据库都使用UTF8编码
- **权限问题**: 确保用户有足够的权限创建表和函数
- **扩展问题**: 确保uuid-ossp扩展已安装

### 2. 调试查询
```sql
-- 查看查询计划
EXPLAIN ANALYZE SELECT * FROM v_comprehensive_statistics;

-- 查看表大小
SELECT 
    schemaname,
    tablename,
    pg_size_pretty(pg_total_relation_size(schemaname||'.'||tablename)) as size
FROM pg_tables 
WHERE schemaname = 'public'
ORDER BY pg_total_relation_size(schemaname||'.'||tablename) DESC;
```

## 联系支持

如有问题或建议，请联系开发团队。

---
*最后更新: 2024年*
