-- 修复 manage_device 表中 nickname 字段的默认值问题
-- 方案1：为 nickname 字段添加默认值
ALTER TABLE `manage_device` MODIFY COLUMN `nickname` varchar(64) CHARACTER SET utf8 COLLATE utf8_general_ci NOT NULL DEFAULT '' COMMENT 'custom name of the device';

-- 方案2：如果希望允许 NULL 值（不推荐，因为业务逻辑中nickname是必需的）
-- ALTER TABLE `manage_device` MODIFY COLUMN `nickname` varchar(64) CHARACTER SET utf8 COLLATE utf8_general_ci DEFAULT '' COMMENT 'custom name of the device';

