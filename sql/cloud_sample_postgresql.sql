-- PostgreSQL版本的DJI Cloud Sample数据库
-- 从MySQL转换而来

-- 创建数据库
-- CREATE DATABASE dji_cloud;

-- 连接到数据库
-- \c dji_cloud;

-- 设置字符集
-- SET client_encoding = 'UTF8';

-- =============================================
-- 设备飞行区域表
-- =============================================

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

-- =============================================
-- 飞行区域文件表
-- =============================================

DROP TABLE IF EXISTS flight_area_file CASCADE;

CREATE TABLE flight_area_file (
    id SERIAL PRIMARY KEY,
    file_id VARCHAR(64) NOT NULL UNIQUE,
    workspace_id VARCHAR(64) NOT NULL,
    name VARCHAR(100) NOT NULL,
    object_key VARCHAR(1000) NOT NULL,
    sign VARCHAR(64) NOT NULL, -- sha256
    size INTEGER NOT NULL,
    latest BOOLEAN NOT NULL, -- The latest version?
    create_time BIGINT NOT NULL,
    update_time BIGINT NOT NULL
);

-- =============================================
-- 飞行区域属性表
-- =============================================

DROP TABLE IF EXISTS flight_area_property CASCADE;

CREATE TABLE flight_area_property (
    id SERIAL PRIMARY KEY,
    element_id VARCHAR(64) NOT NULL UNIQUE,
    type VARCHAR(32) NOT NULL, -- dfence/nfz
    enable BOOLEAN NOT NULL,
    sub_type VARCHAR(32) DEFAULT NULL, -- options: Circle
    radius INTEGER NOT NULL DEFAULT 0 -- unit: cm
);

-- =============================================
-- 日志文件表
-- =============================================

DROP TABLE IF EXISTS logs_file CASCADE;

CREATE TABLE logs_file (
    id BIGSERIAL PRIMARY KEY,
    file_id VARCHAR(45) NOT NULL UNIQUE DEFAULT '', -- uuid
    name VARCHAR(100) NOT NULL DEFAULT '', -- The name of the file in the bucket
    size BIGINT NOT NULL DEFAULT 0, -- file size
    logs_id VARCHAR(45) NOT NULL DEFAULT '', -- The logs_id in the manage_device_logs table
    device_sn VARCHAR(45) NOT NULL DEFAULT '', -- The sn of the device
    fingerprint VARCHAR(64) NOT NULL DEFAULT '', -- file fingerprint
    object_key VARCHAR(1000) NOT NULL DEFAULT '', -- The key of the file in the bucket
    status BOOLEAN NOT NULL, -- Whether the upload was successful. true: success; false: failed
    create_time BIGINT NOT NULL,
    update_time BIGINT NOT NULL
);

COMMENT ON TABLE logs_file IS 'Logs file information';

-- =============================================
-- 日志文件索引表
-- =============================================

DROP TABLE IF EXISTS logs_file_index CASCADE;

CREATE TABLE logs_file_index (
    id BIGSERIAL PRIMARY KEY,
    boot_index INTEGER NOT NULL, -- The file index reported by the dock
    file_id VARCHAR(64) NOT NULL DEFAULT '', -- The file_id in the logs_file table
    start_time BIGINT NOT NULL, -- The file start time reported by the dock
    end_time BIGINT NOT NULL, -- The file end time reported by the dock
    size BIGINT NOT NULL, -- The file size reported by the dock
    device_sn VARCHAR(64) NOT NULL DEFAULT '', -- The sn of the device
    domain INTEGER NOT NULL, -- This parameter corresponds to the domain in the device dictionary table
    create_time BIGINT NOT NULL,
    update_time BIGINT NOT NULL
);

COMMENT ON TABLE logs_file_index IS 'The boot index table corresponding to the logs file';

-- =============================================
-- 设备管理表
-- =============================================

DROP TABLE IF EXISTS manage_device CASCADE;

CREATE TABLE manage_device (
    id SERIAL PRIMARY KEY,
    device_sn VARCHAR(32) NOT NULL UNIQUE DEFAULT '', -- dock, drone, remote control
    device_name VARCHAR(64) NOT NULL DEFAULT 'undefined', -- model of the device
    user_id VARCHAR(64) DEFAULT '', -- The account used when the device was bound
    nickname VARCHAR(64) NOT NULL, -- custom name of the device
    workspace_id VARCHAR(64) DEFAULT '', -- The workspace to which the current device belongs
    device_type INTEGER NOT NULL DEFAULT -1, -- This parameter corresponds to the device type in the device dictionary table
    sub_type INTEGER NOT NULL DEFAULT -1, -- This parameter corresponds to the sub type in the device dictionary table
    domain INTEGER NOT NULL DEFAULT -1, -- This parameter corresponds to the domain in the device dictionary table
    firmware_version VARCHAR(32) DEFAULT '', -- firmware version of the device
    compatible_status BOOLEAN NOT NULL DEFAULT true, -- true: consistent; false: inconsistent
    version VARCHAR(32) DEFAULT '', -- version of the protocol
    device_index VARCHAR(32) DEFAULT '', -- Control of the drone, A control or B control
    child_sn VARCHAR(32) DEFAULT '', -- The device controlled by the gateway
    create_time BIGINT NOT NULL,
    update_time BIGINT NOT NULL,
    bound_time BIGINT DEFAULT NULL, -- The time when the device is bound to the workspace
    bound_status BOOLEAN NOT NULL DEFAULT false, -- The status when the device is bound to the workspace. true: bound; false: not bound
    login_time BIGINT DEFAULT NULL, -- The time of the last device login
    device_desc VARCHAR(100) DEFAULT '',
    url_normal VARCHAR(200) DEFAULT '', -- The icon displayed on the remote control
    url_select VARCHAR(200) DEFAULT '' -- The icon displayed on the remote control when it is selected
);

COMMENT ON TABLE manage_device IS 'Device information';

-- =============================================
-- 设备字典表
-- =============================================

DROP TABLE IF EXISTS manage_device_dictionary CASCADE;

CREATE TABLE manage_device_dictionary (
    id SERIAL PRIMARY KEY,
    domain INTEGER NOT NULL, -- 0: drone; 1: payload; 2: remote control; 3: dock
    device_type INTEGER NOT NULL,
    sub_type INTEGER NOT NULL,
    device_name VARCHAR(32) NOT NULL DEFAULT '',
    device_desc VARCHAR(100) DEFAULT NULL
);

COMMENT ON TABLE manage_device_dictionary IS 'Device product enum';

-- 插入设备字典数据
INSERT INTO manage_device_dictionary (id, domain, device_type, sub_type, device_name, device_desc)
VALUES
    (1, 0, 60, 0, 'Matrice 300 RTK', NULL),
    (2, 0, 67, 0, 'Matrice 30', NULL),
    (3, 0, 67, 1, 'Matrice 30T', NULL),
    (4, 1, 20, 0, 'Z30', NULL),
    (5, 1, 26, 0, 'XT2', NULL),
    (6, 1, 39, 0, 'FPV', NULL),
    (7, 1, 41, 0, 'XTS', NULL),
    (8, 1, 42, 0, 'H20', NULL),
    (9, 1, 43, 0, 'H20T', NULL),
    (10, 1, 50, 65535, 'P1', 'include 24 and 35 and 50mm'),
    (11, 1, 52, 0, 'M30 Camera', NULL),
    (12, 1, 53, 0, 'M30T Camera', NULL),
    (13, 1, 61, 0, 'H20N', NULL),
    (14, 1, 165, 0, 'DJI Dock Camera', NULL),
    (15, 1, 90742, 0, 'L1', NULL),
    (16, 2, 56, 0, 'DJI Smart Controller', 'Remote control for M300'),
    (17, 2, 119, 0, 'DJI RC Plus', 'Remote control for M30'),
    (18, 3, 1, 0, 'DJI Dock', ''),
    (19, 0, 77, 0, 'Mavic 3E', NULL),
    (20, 0, 77, 1, 'Mavic 3T', NULL),
    (21, 1, 66, 0, 'Mavic 3E Camera', NULL),
    (22, 1, 67, 0, 'Mavic 3T Camera', NULL),
    (23, 2, 144, 0, 'DJI RC Pro', 'Remote control for Mavic 3E/T and Mavic 3M'),
    (24, 0, 77, 2, 'Mavic 3M', NULL),
    (25, 1, 68, 0, 'Mavic 3M Camera', NULL),
    (26, 0, 89, 0, 'Matrice 350 RTK', NULL),
    (27, 3, 2, 0, 'DJI Dock2', NULL),
    (28, 0, 91, 0, 'M3D', NULL),
    (29, 0, 91, 1, 'M3TD', NULL),
    (30, 1, 80, 0, 'M3D Camera', NULL),
    (31, 1, 81, 0, 'M3TD Camera', NULL);

-- =============================================
-- 设备固件表
-- =============================================

DROP TABLE IF EXISTS manage_device_firmware CASCADE;

CREATE TABLE manage_device_firmware (
    id BIGSERIAL PRIMARY KEY,
    firmware_id VARCHAR(45) NOT NULL UNIQUE DEFAULT '', -- uuid
    file_name VARCHAR(64) NOT NULL DEFAULT 'undefined', -- The file name of the firmware package
    firmware_version VARCHAR(45) NOT NULL DEFAULT '', -- formatted as 00.00.0000
    object_key VARCHAR(200) NOT NULL, -- The object key of the firmware package in the bucket
    file_size BIGINT NOT NULL, -- The size of the firmware package
    file_md5 VARCHAR(45) NOT NULL DEFAULT '', -- The md5 of the firmware package
    workspace_id VARCHAR(64) NOT NULL,
    release_note VARCHAR(1000) NOT NULL DEFAULT '', -- The release note of the firmware package
    release_date BIGINT NOT NULL, -- The release date of the firmware package
    user_name VARCHAR(64) NOT NULL, -- The name of the creator
    status BOOLEAN NOT NULL DEFAULT false, -- Availability of the firmware package. true: available; false: unavailable
    create_time BIGINT NOT NULL,
    update_time BIGINT NOT NULL
);

COMMENT ON TABLE manage_device_firmware IS 'Firmware file information';

-- =============================================
-- 设备HMS表
-- =============================================

DROP TABLE IF EXISTS manage_device_hms CASCADE;

CREATE TABLE manage_device_hms (
    id SERIAL PRIMARY KEY,
    hms_id VARCHAR(45) NOT NULL UNIQUE DEFAULT '', -- uuid
    tid VARCHAR(45) NOT NULL DEFAULT '', -- The tid when the device reports the hms message
    bid VARCHAR(45) NOT NULL DEFAULT '', -- The bid when the device reports the hms message
    sn VARCHAR(45) NOT NULL DEFAULT '', -- Which device reported the message
    level SMALLINT NOT NULL, -- hms level. 0: notice; 1: caution; 2: warning
    module SMALLINT NOT NULL, -- Which module's message. 0: flight task; 1:device manage; 2: media; 3: hms
    hms_key VARCHAR(64) NOT NULL DEFAULT '', -- The key of the hms message
    message_zh VARCHAR(100) NOT NULL DEFAULT '', -- Chinese message
    message_en VARCHAR(300) NOT NULL DEFAULT '', -- English message
    create_time BIGINT NOT NULL,
    update_time BIGINT NOT NULL
);

COMMENT ON TABLE manage_device_hms IS 'Device''s hms information';

-- =============================================
-- 设备日志表
-- =============================================

DROP TABLE IF EXISTS manage_device_logs CASCADE;

CREATE TABLE manage_device_logs (
    id BIGSERIAL PRIMARY KEY,
    logs_id VARCHAR(45) NOT NULL UNIQUE DEFAULT '', -- uuid
    username VARCHAR(100) NOT NULL DEFAULT '', -- The name of the creator
    device_sn VARCHAR(45) NOT NULL DEFAULT '', -- The sn of the device
    logs_info VARCHAR(1000) NOT NULL DEFAULT '', -- A description of the log issue
    happen_time BIGINT DEFAULT NULL, -- The time when the logging problem occurred
    status SMALLINT NOT NULL, -- 1: uploading; 2: done 3: canceled; 4: failed
    update_time BIGINT NOT NULL,
    create_time BIGINT NOT NULL
);

COMMENT ON TABLE manage_device_logs IS 'Log for uploading logs';

-- =============================================
-- 设备载荷表
-- =============================================

DROP TABLE IF EXISTS manage_device_payload CASCADE;

CREATE TABLE manage_device_payload (
    id SERIAL PRIMARY KEY,
    payload_sn VARCHAR(32) NOT NULL UNIQUE DEFAULT '', -- The sn of the device payload
    payload_name VARCHAR(64) NOT NULL DEFAULT 'undefined', -- model of the payload
    payload_type SMALLINT NOT NULL, -- This parameter corresponds to the device type in the device dictionary table
    sub_type SMALLINT NOT NULL, -- This parameter corresponds to the sub type in the device dictionary table
    firmware_version VARCHAR(32) DEFAULT NULL, -- firmware version of the device payload
    payload_index SMALLINT NOT NULL, -- The location of the payload on the device
    device_sn VARCHAR(32) NOT NULL DEFAULT '', -- Which device the current payload belongs to
    payload_desc VARCHAR(100) DEFAULT NULL,
    control_source VARCHAR(1) DEFAULT NULL,
    create_time BIGINT NOT NULL,
    update_time BIGINT NOT NULL
);

COMMENT ON TABLE manage_device_payload IS 'The payload information of the device.';

-- =============================================
-- 固件型号表
-- =============================================

DROP TABLE IF EXISTS manage_firmware_model CASCADE;

CREATE TABLE manage_firmware_model (
    id BIGSERIAL PRIMARY KEY,
    firmware_id VARCHAR(64) NOT NULL,
    device_name VARCHAR(64) NOT NULL, -- model of the device
    create_time BIGINT NOT NULL,
    update_time BIGINT NOT NULL
);

-- =============================================
-- 用户管理表
-- =============================================

DROP TABLE IF EXISTS manage_user CASCADE;

CREATE TABLE manage_user (
    id SERIAL PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL UNIQUE DEFAULT '', -- uuid
    username VARCHAR(32) NOT NULL DEFAULT '', -- The name of the account
    password VARCHAR(32) NOT NULL DEFAULT '', -- The password of the account
    workspace_id VARCHAR(64) NOT NULL DEFAULT '', -- Which workspace the current account belongs to
    user_type SMALLINT NOT NULL, -- The type of account. 1: web; 2: pilot
    mqtt_username VARCHAR(32) NOT NULL DEFAULT '', -- The account name used by the current account when logging into the emqx server
    mqtt_password VARCHAR(32) NOT NULL DEFAULT '', -- The account password used by the current account when logging into the emqx server
    create_time BIGINT NOT NULL,
    update_time BIGINT NOT NULL
);

COMMENT ON TABLE manage_user IS 'System account.';

-- 插入用户数据
INSERT INTO manage_user (id, user_id, username, password, workspace_id, user_type, mqtt_username, mqtt_password, create_time, update_time)
VALUES
    (1,'a1559e7c-8dd8-4780-b952-100cc4797da2','adminPC','adminPC','e3dea0f5-37f2-4d79-ae58-490af3228069',1,'admin','admin',1634898410751,1650880112310),
    (2,'be7c6c3d-afe9-4be4-b9eb-c55066c0914e','pilot','pilot123','e3dea0f5-37f2-4d79-ae58-490af3228069',2,'pilot','pilot123',1634898410751,1634898410751);

-- =============================================
-- 工作空间表
-- =============================================

DROP TABLE IF EXISTS manage_workspace CASCADE;

CREATE TABLE manage_workspace (
    id SERIAL PRIMARY KEY,
    workspace_id VARCHAR(64) NOT NULL UNIQUE DEFAULT '', -- uuid
    workspace_name VARCHAR(64) NOT NULL DEFAULT '', -- The name of the workspace
    workspace_desc VARCHAR(100) NOT NULL DEFAULT '', -- The description of the workspace
    platform_name VARCHAR(64) NOT NULL DEFAULT '', -- The platform name of the workspace
    create_time BIGINT NOT NULL,
    update_time BIGINT NOT NULL,
    bind_code VARCHAR(32) NOT NULL UNIQUE DEFAULT '' -- The binding code for this workspace
);

-- 插入工作空间数据
INSERT INTO manage_workspace (id, workspace_id, workspace_name, workspace_desc, platform_name, create_time, update_time, bind_code)
VALUES
    (1,'e3dea0f5-37f2-4d79-ae58-490af3228069','Test Group One','Cloud Sample Test Platform','Cloud Api Platform',1634898410751,1634898410751,'qwe');

-- =============================================
-- 地图元素坐标表
-- =============================================

DROP TABLE IF EXISTS map_element_coordinate CASCADE;

CREATE TABLE map_element_coordinate (
    id SERIAL PRIMARY KEY,
    element_id VARCHAR(64) NOT NULL DEFAULT '', -- The element_id in the logs_file table
    longitude DECIMAL(18,14) NOT NULL, -- The longitude of this element
    latitude DECIMAL(17,14) NOT NULL, -- The latitude of this element
    altitude DECIMAL(17,14) DEFAULT NULL -- The altitude of this element. If the element is point, it is null
);

COMMENT ON TABLE map_element_coordinate IS 'The coordinate information corresponding to the element.';

-- =============================================
-- 地图组表
-- =============================================

DROP TABLE IF EXISTS map_group CASCADE;

CREATE TABLE map_group (
    id SERIAL PRIMARY KEY,
    group_id VARCHAR(64) NOT NULL UNIQUE DEFAULT '', -- uuid
    group_name VARCHAR(64) NOT NULL DEFAULT '', -- The name of the group
    group_type INTEGER NOT NULL, -- The type of the group. 0: custome; 1: default; 2: app shared
    workspace_id VARCHAR(64) NOT NULL DEFAULT '', -- The workspace_id in the manage_workspace table
    is_distributed BOOLEAN NOT NULL DEFAULT true, -- element group distributed status. Only data with value true is displayed on the pilot map
    is_lock BOOLEAN NOT NULL DEFAULT false, -- Whether to lock. If locked, the elements under this element group cannot be deleted and modified
    create_time BIGINT NOT NULL,
    update_time BIGINT NOT NULL
);

COMMENT ON TABLE map_group IS 'The group information of the map element.';

-- 插入地图组数据
INSERT INTO map_group (id, group_id, group_name, group_type, workspace_id, is_distributed, is_lock, create_time, update_time)
VALUES
    (1,'e3dea0f5-37f2-4d79-ae58-490af3228060','Pilot Share Layer',2,'e3dea0f5-37f2-4d79-ae58-490af3228069',true,false,1638330077356,1638330077356),
    (2,'e3dea0f5-37f2-4d79-ae58-490af3228011','Default Layer',1,'e3dea0f5-37f2-4d79-ae58-490af3228069',true,false,1638330077356,1638330077356),
    (3,'d58479c8-4a80-4036-aa55-8beffb7158e9','Custom Flight Area',0,'e3dea0f5-37f2-4d79-ae58-490af3228069',true,false,1638330077356,1638330077356);

-- =============================================
-- 地图组元素表
-- =============================================

DROP TABLE IF EXISTS map_group_element CASCADE;

CREATE TABLE map_group_element (
    id SERIAL PRIMARY KEY,
    element_id VARCHAR(64) NOT NULL UNIQUE DEFAULT '', -- uuid
    element_name VARCHAR(64) NOT NULL DEFAULT '', -- The name of the element
    display SMALLINT NOT NULL DEFAULT 1, -- It no longer works
    group_id VARCHAR(64) NOT NULL DEFAULT '', -- The group_id in the map_group table
    element_type SMALLINT NOT NULL, -- element type. 0: point; 1: line; 2: polygon
    username VARCHAR(64) NOT NULL DEFAULT '', -- The name of the creator
    color VARCHAR(32) NOT NULL DEFAULT '', -- The color of the element. Hexadecimal
    clamp_to_ground BOOLEAN NOT NULL DEFAULT false, -- Whether it is on the ground
    create_time BIGINT NOT NULL,
    update_time BIGINT NOT NULL
);

COMMENT ON TABLE map_group_element IS 'Information about the element corresponding to the group.';

-- =============================================
-- 媒体文件表
-- =============================================

DROP TABLE IF EXISTS media_file CASCADE;

CREATE TABLE media_file (
    id SERIAL PRIMARY KEY,
    file_id VARCHAR(64) NOT NULL UNIQUE DEFAULT '', -- uuid
    file_name VARCHAR(100) NOT NULL DEFAULT '', -- The original name of the file
    file_path VARCHAR(1000) NOT NULL DEFAULT '', -- The path of the file
    workspace_id VARCHAR(64) NOT NULL DEFAULT '', -- The workspace to which the file belongs
    fingerprint VARCHAR(64) DEFAULT '', -- The fingerprint of the file
    tinny_fingerprint VARCHAR(100) DEFAULT '', -- The tiny fingerprint of the file
    object_key VARCHAR(1000) NOT NULL DEFAULT '', -- The key of the file in the bucket
    sub_file_type INTEGER DEFAULT NULL, -- This property exists only for image files uploaded by Pilot. 0: normal picture; 1: panorama
    is_original BOOLEAN NOT NULL, -- Whether is the original image
    drone VARCHAR(32) NOT NULL DEFAULT 'undefined', -- The sn of the drone which create the file
    payload VARCHAR(32) NOT NULL DEFAULT 'undefined', -- The name of the drone payload which create the file
    job_id VARCHAR(64) DEFAULT '', -- The job_id in the wayline_job table
    create_time BIGINT NOT NULL,
    update_time BIGINT NOT NULL
);

COMMENT ON TABLE media_file IS 'Media file information';

-- =============================================
-- 航线文件表
-- =============================================

DROP TABLE IF EXISTS wayline_file CASCADE;

CREATE TABLE wayline_file (
    id SERIAL PRIMARY KEY,
    name VARCHAR(64) NOT NULL DEFAULT '', -- wayline name
    wayline_id VARCHAR(64) NOT NULL UNIQUE DEFAULT '', -- uuid
    drone_model_key VARCHAR(32) NOT NULL DEFAULT '', -- device product enum. format: domain-device_type-sub_type
    payload_model_keys VARCHAR(200) DEFAULT NULL, -- payload product enum. format: domain-device_type-sub_type
    workspace_id VARCHAR(64) NOT NULL DEFAULT '', -- Which workspace the current wayline belongs to
    sign VARCHAR(64) NOT NULL DEFAULT '', -- The md5 of the wayline file
    favorited BOOLEAN NOT NULL DEFAULT false, -- Whether the file is favorited or not
    template_types VARCHAR(32) NOT NULL DEFAULT '', -- wayline file template type. 0: waypoint
    object_key VARCHAR(200) NOT NULL DEFAULT '', -- The key of the file in the bucket
    user_name VARCHAR(64) NOT NULL DEFAULT '', -- The name of the creator
    create_time BIGINT NOT NULL,
    update_time BIGINT NOT NULL -- required, can't modify
);

COMMENT ON TABLE wayline_file IS 'Wayline file information';

-- =============================================
-- 航线任务表
-- =============================================

DROP TABLE IF EXISTS wayline_job CASCADE;

CREATE TABLE wayline_job (
    id SERIAL PRIMARY KEY,
    job_id VARCHAR(45) NOT NULL UNIQUE DEFAULT '', -- uuid
    name VARCHAR(64) NOT NULL DEFAULT '', -- The name of the job
    file_id VARCHAR(45) NOT NULL DEFAULT '', -- The wayline file used for this job
    dock_sn VARCHAR(45) NOT NULL DEFAULT '', -- Which dock executes the job
    workspace_id VARCHAR(45) NOT NULL DEFAULT '', -- Which workspace the current job belongs to
    task_type INTEGER NOT NULL,
    wayline_type INTEGER NOT NULL, -- The template type of the wayline
    execute_time BIGINT DEFAULT NULL, -- actual begin time
    completed_time BIGINT DEFAULT NULL, -- actual end time
    username VARCHAR(64) NOT NULL DEFAULT '', -- The name of the creator
    begin_time BIGINT NOT NULL, -- planned begin time
    end_time BIGINT DEFAULT NULL, -- planned end time
    error_code INTEGER DEFAULT NULL,
    status INTEGER NOT NULL, -- 1: pending; 2: in progress; 3: success; 4: cancel; 5: failed
    rth_altitude INTEGER NOT NULL, -- return to home altitude. min: 20m; max: 500m
    out_of_control INTEGER NOT NULL, -- out of control action. 0: go home; 1: hover; 2: landing
    media_count INTEGER NOT NULL DEFAULT 0,
    create_time BIGINT NOT NULL,
    update_time BIGINT NOT NULL,
    parent_id VARCHAR(45) DEFAULT NULL
);

COMMENT ON TABLE wayline_job IS 'Wayline mission information of the dock.';