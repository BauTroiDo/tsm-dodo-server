-- phpMyAdmin SQL Dump
-- version 5.2.1
-- https://www.phpmyadmin.net/
--
-- Host: 127.0.0.1
-- Generation Time: Jun 26, 2026 at 03:49 AM
-- Server version: 10.4.32-MariaDB
-- PHP Version: 8.0.30

SET SQL_MODE = "NO_AUTO_VALUE_ON_ZERO";
START TRANSACTION;
SET time_zone = "+00:00";


/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8mb4 */;

--
-- Database: `tsm_dodo`
--

-- --------------------------------------------------------

--
-- Table structure for table `accounts`
--

CREATE TABLE `accounts` (
  `id` bigint(20) NOT NULL,
  `account` varchar(64) NOT NULL,
  `password_hash` varchar(72) NOT NULL,
  `is_suspended` tinyint(1) NOT NULL DEFAULT 0,
  `last_login_at` bigint(20) DEFAULT NULL,
  `created_at` bigint(20) NOT NULL,
  `gm_level` int(11) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

--
-- Dumping data for table `accounts`
--

INSERT INTO `accounts` (`id`, `account`, `password_hash`, `is_suspended`, `last_login_at`, `created_at`, `gm_level`) VALUES
(1, 'zorodies', '$2a$10$gegLeNhvrXD/PTuwQj4k9uj3trFmm8n.ksGWD27DY1q/ZoqDcF1jK', 0, NULL, 1782436661808, 0);

-- --------------------------------------------------------

--
-- Table structure for table `characters`
--

CREATE TABLE `characters` (
  `id` bigint(20) NOT NULL,
  `account_id` bigint(20) NOT NULL,
  `name` varchar(32) NOT NULL,
  `sex` int(11) NOT NULL DEFAULT 1,
  `hair` int(11) NOT NULL DEFAULT 0,
  `color1` int(11) NOT NULL DEFAULT 0,
  `color2` int(11) NOT NULL DEFAULT 0,
  `element` int(11) NOT NULL DEFAULT 1,
  `level` int(11) NOT NULL DEFAULT 1,
  `map_id` int(11) NOT NULL DEFAULT 10801,
  `x` int(11) NOT NULL DEFAULT 570,
  `y` int(11) NOT NULL DEFAULT 770,
  `str` int(11) NOT NULL DEFAULT 0,
  `dex` int(11) NOT NULL DEFAULT 0,
  `vit` int(11) NOT NULL DEFAULT 0,
  `int_` int(11) NOT NULL DEFAULT 0,
  `men` int(11) NOT NULL DEFAULT 0,
  `spr` int(11) NOT NULL DEFAULT 0,
  `skill_point` int(11) NOT NULL DEFAULT 0,
  `attr_point` int(11) NOT NULL DEFAULT 0,
  `exp` bigint(20) NOT NULL DEFAULT 0,
  `turn` int(11) NOT NULL DEFAULT 0,
  `record_map_id` int(11) NOT NULL DEFAULT 10801,
  `record_x` int(11) NOT NULL DEFAULT 570,
  `record_y` int(11) NOT NULL DEFAULT 770,
  `fight_npc_id` int(11) NOT NULL DEFAULT 0,
  `title_id` int(11) NOT NULL DEFAULT 0,
  `created_at` bigint(20) NOT NULL,
  `point_real` int(11) NOT NULL DEFAULT 0,
  `point_virtual` int(11) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

--
-- Dumping data for table `characters`
--

INSERT INTO `characters` (`id`, `account_id`, `name`, `sex`, `hair`, `color1`, `color2`, `element`, `level`, `map_id`, `x`, `y`, `str`, `dex`, `vit`, `int_`, `men`, `spr`, `skill_point`, `attr_point`, `exp`, `turn`, `record_map_id`, `record_x`, `record_y`, `fight_npc_id`, `title_id`, `created_at`, `point_real`, `point_virtual`) VALUES
(1, 1, 'zorodies', 2, 1, 444997434, 859444444, 2, 3, 12136, 490, 350, 0, 9, 0, 0, 0, 0, 5, 3, 2, 0, 10801, 570, 770, 0, 0, 1782436729574, 0, 0);

-- --------------------------------------------------------

--
-- Table structure for table `character_bit_flags`
--

CREATE TABLE `character_bit_flags` (
  `char_id` bigint(20) NOT NULL,
  `flag_id` int(11) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

--
-- Dumping data for table `character_bit_flags`
--

INSERT INTO `character_bit_flags` (`char_id`, `flag_id`) VALUES
(1, 1400),
(1, 1401),
(1, 1402),
(1, 1403),
(1, 1404),
(1, 1405),
(1, 1412),
(1, 1419);

-- --------------------------------------------------------

--
-- Table structure for table `character_completed_events`
--

CREATE TABLE `character_completed_events` (
  `char_id` bigint(20) NOT NULL,
  `map_id` int(11) NOT NULL,
  `eve_no` int(11) NOT NULL,
  `completion_count` int(11) NOT NULL DEFAULT 1
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

--
-- Dumping data for table `character_completed_events`
--

INSERT INTO `character_completed_events` (`char_id`, `map_id`, `eve_no`, `completion_count`) VALUES
(1, 10801, 1, 1),
(1, 10801, 2, 1),
(1, 10801, 4, 4),
(1, 10851, 1, 3),
(1, 10851, 2, 2),
(1, 10851, 3, 1),
(1, 10851, 4, 3),
(1, 10851, 6, 1),
(1, 10851, 11, 4),
(1, 12002, 4, 1),
(1, 12003, 1, 1),
(1, 12136, 2, 2);

-- --------------------------------------------------------

--
-- Table structure for table `character_dispatches`
--

CREATE TABLE `character_dispatches` (
  `id` bigint(20) NOT NULL,
  `character_id` bigint(20) NOT NULL,
  `slot_index` int(11) NOT NULL,
  `dispatch_kind` int(11) NOT NULL,
  `inn_pet_index` int(11) NOT NULL,
  `start_time` double NOT NULL,
  `end_time` double NOT NULL,
  `effect_index1` int(11) NOT NULL DEFAULT 0,
  `effect_index2` int(11) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- --------------------------------------------------------

--
-- Table structure for table `character_fly_points`
--

CREATE TABLE `character_fly_points` (
  `char_id` bigint(20) NOT NULL,
  `point_index` int(11) NOT NULL,
  `scene_id` int(11) NOT NULL,
  `x` int(11) NOT NULL,
  `y` int(11) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- --------------------------------------------------------

--
-- Table structure for table `character_hotkeys`
--

CREATE TABLE `character_hotkeys` (
  `id` bigint(20) NOT NULL,
  `character_id` bigint(20) NOT NULL,
  `data_index` int(11) NOT NULL,
  `key_index` int(11) NOT NULL,
  `use_id` int(11) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- --------------------------------------------------------

--
-- Table structure for table `character_missions`
--

CREATE TABLE `character_missions` (
  `char_id` bigint(20) NOT NULL,
  `mission_id` int(11) NOT NULL,
  `step` int(11) NOT NULL DEFAULT 1
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

--
-- Dumping data for table `character_missions`
--

INSERT INTO `character_missions` (`char_id`, `mission_id`, `step`) VALUES
(1, 10001, 1),
(1, 10037, 1),
(1, 10049, 1),
(1, 11804, 1),
(1, 12281, 1),
(1, 12283, 1),
(1, 12285, 1),
(1, 12287, 1),
(1, 12394, 1),
(1, 12396, 1),
(1, 12398, 1);

-- --------------------------------------------------------

--
-- Table structure for table `character_mission_flags`
--

CREATE TABLE `character_mission_flags` (
  `char_id` bigint(20) NOT NULL,
  `bit_index` int(11) NOT NULL,
  `bit_value` int(11) NOT NULL DEFAULT 1
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

--
-- Dumping data for table `character_mission_flags`
--

INSERT INTO `character_mission_flags` (`char_id`, `bit_index`, `bit_value`) VALUES
(1, 19, 1),
(1, 27, 1),
(1, 936, 1),
(1, 1374, 1),
(1, 1375, 1),
(1, 1376, 1),
(1, 1377, 1),
(1, 1432, 1),
(1, 1434, 1),
(1, 1436, 1);

-- --------------------------------------------------------

--
-- Table structure for table `character_money`
--

CREATE TABLE `character_money` (
  `character_id` bigint(20) NOT NULL,
  `gold` int(11) NOT NULL DEFAULT 0,
  `auction_gold` int(11) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

--
-- Dumping data for table `character_money`
--

INSERT INTO `character_money` (`character_id`, `gold`, `auction_gold`) VALUES
(1, 85, 0);

-- --------------------------------------------------------

--
-- Table structure for table `character_mounts`
--

CREATE TABLE `character_mounts` (
  `id` bigint(20) NOT NULL,
  `character_id` bigint(20) NOT NULL,
  `level` int(11) NOT NULL DEFAULT 1,
  `attr_point_1` int(11) NOT NULL DEFAULT 0,
  `attr_point_2` int(11) NOT NULL DEFAULT 0,
  `attr_point_3` int(11) NOT NULL DEFAULT 0,
  `attr_point_4` int(11) NOT NULL DEFAULT 0,
  `attr_point_5` int(11) NOT NULL DEFAULT 0,
  `attr_point_6` int(11) NOT NULL DEFAULT 0,
  `current_npc_id` int(11) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

--
-- Dumping data for table `character_mounts`
--

INSERT INTO `character_mounts` (`id`, `character_id`, `level`, `attr_point_1`, `attr_point_2`, `attr_point_3`, `attr_point_4`, `attr_point_5`, `attr_point_6`, `current_npc_id`) VALUES
(1, 1, 1, 0, 0, 0, 0, 0, 0, 0);

-- --------------------------------------------------------

--
-- Table structure for table `character_mount_equips`
--

CREATE TABLE `character_mount_equips` (
  `id` bigint(20) NOT NULL,
  `character_id` bigint(20) NOT NULL,
  `fit_type` int(11) NOT NULL,
  `item_id` int(11) NOT NULL,
  `quantity` int(11) NOT NULL DEFAULT 1,
  `damage` int(11) NOT NULL DEFAULT 0,
  `element` int(11) NOT NULL DEFAULT 0,
  `element_value` int(11) NOT NULL DEFAULT 0,
  `proof_kind` int(11) NOT NULL DEFAULT 0,
  `grow_level` int(11) NOT NULL DEFAULT 0,
  `grow_exp` int(11) NOT NULL DEFAULT 0,
  `special_kind` int(11) NOT NULL DEFAULT 0,
  `stone_attr` int(11) NOT NULL DEFAULT 0,
  `stone_level` int(11) NOT NULL DEFAULT 0,
  `enhance_level` int(11) NOT NULL DEFAULT 0,
  `delete_time` double NOT NULL DEFAULT 0,
  `damaged_item_id` int(11) NOT NULL DEFAULT 0,
  `is_locked` tinyint(1) NOT NULL DEFAULT 0,
  `reinforced` int(11) NOT NULL DEFAULT 0,
  `affix1` int(11) NOT NULL DEFAULT 0,
  `affix2` int(11) NOT NULL DEFAULT 0,
  `affix3` int(11) NOT NULL DEFAULT 0,
  `style_level` int(11) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- --------------------------------------------------------

--
-- Table structure for table `character_pets`
--

CREATE TABLE `character_pets` (
  `id` bigint(20) NOT NULL,
  `character_id` bigint(20) NOT NULL,
  `storage_type` int(11) NOT NULL DEFAULT 1,
  `slot_index` int(11) NOT NULL,
  `npc_id` int(11) NOT NULL,
  `name` varchar(64) NOT NULL DEFAULT '',
  `level` int(11) NOT NULL DEFAULT 1,
  `exp` bigint(20) NOT NULL DEFAULT 0,
  `hp` bigint(20) NOT NULL DEFAULT 0,
  `sp` int(11) NOT NULL DEFAULT 0,
  `atk` int(11) NOT NULL DEFAULT 0,
  `def` int(11) NOT NULL DEFAULT 0,
  `agi` int(11) NOT NULL DEFAULT 0,
  `intelligence` int(11) NOT NULL DEFAULT 0,
  `hpx` int(11) NOT NULL DEFAULT 0,
  `spx` int(11) NOT NULL DEFAULT 0,
  `death_count` int(11) NOT NULL DEFAULT 0,
  `loyalty` int(11) NOT NULL DEFAULT 60,
  `skill_point` int(11) NOT NULL DEFAULT 0,
  `skill1_lv` int(11) NOT NULL DEFAULT 0,
  `skill2_lv` int(11) NOT NULL DEFAULT 0,
  `skill3_lv` int(11) NOT NULL DEFAULT 0,
  `can_grow` int(11) NOT NULL DEFAULT 1,
  `soul_id` int(11) NOT NULL DEFAULT 0,
  `upgrade_lv` int(11) NOT NULL DEFAULT 0,
  `ascend_pill_count` int(11) NOT NULL DEFAULT 0,
  `can_use_special_skill` tinyint(1) NOT NULL DEFAULT 0,
  `hp_pill_count` int(11) NOT NULL DEFAULT 0,
  `sp_pill_count` int(11) NOT NULL DEFAULT 0,
  `skill4_lv` int(11) NOT NULL DEFAULT 0,
  `conjure_grow` int(11) NOT NULL DEFAULT 0,
  `turn_kind` int(11) NOT NULL DEFAULT 0,
  `surplus` int(11) NOT NULL DEFAULT 0,
  `lv_up_time` double NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

--
-- Dumping data for table `character_pets`
--

INSERT INTO `character_pets` (`id`, `character_id`, `storage_type`, `slot_index`, `npc_id`, `name`, `level`, `exp`, `hp`, `sp`, `atk`, `def`, `agi`, `intelligence`, `hpx`, `spx`, `death_count`, `loyalty`, `skill_point`, `skill1_lv`, `skill2_lv`, `skill3_lv`, `can_grow`, `soul_id`, `upgrade_lv`, `ascend_pill_count`, `can_use_special_skill`, `hp_pill_count`, `sp_pill_count`, `skill4_lv`, `conjure_grow`, `turn_kind`, `surplus`, `lv_up_time`) VALUES
(1, 1, 1, 1, 22082, '地巴豆妖', 3, 0, 66, 69, 1, 4, 1, 1, 5, 2, 0, 60, 0, 1, 1, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);

-- --------------------------------------------------------

--
-- Table structure for table `character_pet_equips`
--

CREATE TABLE `character_pet_equips` (
  `id` bigint(20) NOT NULL,
  `character_id` bigint(20) NOT NULL,
  `storage_type` int(11) NOT NULL,
  `slot_index` int(11) NOT NULL,
  `fit_type` int(11) NOT NULL,
  `item_id` int(11) NOT NULL,
  `quantity` int(11) NOT NULL DEFAULT 1,
  `damage` int(11) NOT NULL DEFAULT 0,
  `element` int(11) NOT NULL DEFAULT 0,
  `element_value` int(11) NOT NULL DEFAULT 0,
  `proof_kind` int(11) NOT NULL DEFAULT 0,
  `grow_level` int(11) NOT NULL DEFAULT 0,
  `grow_exp` int(11) NOT NULL DEFAULT 0,
  `special_kind` int(11) NOT NULL DEFAULT 0,
  `stone_attr` int(11) NOT NULL DEFAULT 0,
  `stone_level` int(11) NOT NULL DEFAULT 0,
  `enhance_level` int(11) NOT NULL DEFAULT 0,
  `delete_time` double NOT NULL DEFAULT 0,
  `damaged_item_id` int(11) NOT NULL DEFAULT 0,
  `is_locked` tinyint(1) NOT NULL DEFAULT 0,
  `reinforced` int(11) NOT NULL DEFAULT 0,
  `affix1` int(11) NOT NULL DEFAULT 0,
  `affix2` int(11) NOT NULL DEFAULT 0,
  `affix3` int(11) NOT NULL DEFAULT 0,
  `style_level` int(11) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- --------------------------------------------------------

--
-- Table structure for table `character_role_counts`
--

CREATE TABLE `character_role_counts` (
  `char_id` bigint(20) NOT NULL,
  `count_index` int(11) NOT NULL,
  `value` int(11) NOT NULL DEFAULT 0,
  `max_value` int(11) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

--
-- Dumping data for table `character_role_counts`
--

INSERT INTO `character_role_counts` (`char_id`, `count_index`, `value`, `max_value`) VALUES
(1, 101, 0, 0),
(1, 102, 0, 0),
(1, 103, 0, 0),
(1, 106, 0, 0),
(1, 124, 0, 0),
(1, 125, 0, 0),
(1, 126, 0, 0),
(1, 676, 14, 0),
(1, 677, 0, 0),
(1, 822, 0, 0);

-- --------------------------------------------------------

--
-- Table structure for table `character_skills`
--

CREATE TABLE `character_skills` (
  `character_id` bigint(20) NOT NULL,
  `skill_id` int(11) NOT NULL,
  `level` int(11) NOT NULL DEFAULT 1
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

--
-- Dumping data for table `character_skills`
--

INSERT INTO `character_skills` (`character_id`, `skill_id`, `level`) VALUES
(1, 11001, 1),
(1, 14001, 1);

-- --------------------------------------------------------

--
-- Table structure for table `character_ui_sell_counts`
--

CREATE TABLE `character_ui_sell_counts` (
  `char_id` bigint(20) NOT NULL,
  `sell_id` int(11) NOT NULL,
  `count` int(11) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- --------------------------------------------------------

--
-- Table structure for table `friends`
--

CREATE TABLE `friends` (
  `char_id` bigint(20) NOT NULL,
  `friend_id` bigint(20) NOT NULL,
  `friendly` int(11) NOT NULL DEFAULT 0,
  `function_flag` int(11) NOT NULL DEFAULT 0,
  `add_time` double NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- --------------------------------------------------------

--
-- Table structure for table `friend_invites`
--

CREATE TABLE `friend_invites` (
  `id` bigint(20) NOT NULL,
  `target_char_id` bigint(20) NOT NULL,
  `inviter_char_id` bigint(20) NOT NULL,
  `created_at` bigint(20) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- --------------------------------------------------------

--
-- Table structure for table `inventories`
--

CREATE TABLE `inventories` (
  `character_id` bigint(20) NOT NULL,
  `storage_type` int(11) NOT NULL,
  `slot` int(11) NOT NULL,
  `item_id` int(11) NOT NULL,
  `quantity` int(11) NOT NULL,
  `damage` int(11) NOT NULL DEFAULT 0,
  `element` int(11) NOT NULL DEFAULT 0,
  `element_value` int(11) NOT NULL DEFAULT 0,
  `proof_kind` int(11) NOT NULL DEFAULT 0,
  `grow_level` int(11) NOT NULL DEFAULT 0,
  `grow_exp` int(11) NOT NULL DEFAULT 0,
  `special_kind` int(11) NOT NULL DEFAULT 0,
  `stone_attr` int(11) NOT NULL DEFAULT 0,
  `stone_level` int(11) NOT NULL DEFAULT 0,
  `enhance_level` int(11) NOT NULL DEFAULT 0,
  `delete_time` double NOT NULL DEFAULT 0,
  `damaged_item_id` int(11) NOT NULL DEFAULT 0,
  `is_locked` tinyint(1) NOT NULL DEFAULT 0,
  `reinforced` int(11) NOT NULL DEFAULT 0,
  `affix1` int(11) NOT NULL DEFAULT 0,
  `affix2` int(11) NOT NULL DEFAULT 0,
  `affix3` int(11) NOT NULL DEFAULT 0,
  `style_level` int(11) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

--
-- Dumping data for table `inventories`
--

INSERT INTO `inventories` (`character_id`, `storage_type`, `slot`, `item_id`, `quantity`, `damage`, `element`, `element_value`, `proof_kind`, `grow_level`, `grow_exp`, `special_kind`, `stone_attr`, `stone_level`, `enhance_level`, `delete_time`, `damaged_item_id`, `is_locked`, `reinforced`, `affix1`, `affix2`, `affix3`, `style_level`) VALUES
(1, 1, 0, 26460, 100, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
(1, 1, 1, 26461, 100, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
(1, 1, 2, 50001, 3, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
(1, 1, 3, 49501, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
(1, 1, 4, 46285, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
(1, 8, 1, 20001, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
(1, 8, 2, 19001, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);

-- --------------------------------------------------------

--
-- Table structure for table `mails`
--

CREATE TABLE `mails` (
  `id` int(11) NOT NULL,
  `char_id` bigint(20) NOT NULL,
  `expire_time` double NOT NULL,
  `state` int(11) NOT NULL DEFAULT 0,
  `content` text NOT NULL,
  `created_at` bigint(20) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- --------------------------------------------------------

--
-- Table structure for table `mail_attachments`
--

CREATE TABLE `mail_attachments` (
  `mail_id` int(11) NOT NULL,
  `slot` int(11) NOT NULL,
  `kind` int(11) NOT NULL,
  `item_id` int(11) NOT NULL DEFAULT 0,
  `quantity` int(11) NOT NULL DEFAULT 0,
  `damage` int(11) NOT NULL DEFAULT 0,
  `element` int(11) NOT NULL DEFAULT 0,
  `element_value` int(11) NOT NULL DEFAULT 0,
  `proof_kind` int(11) NOT NULL DEFAULT 0,
  `grow_level` int(11) NOT NULL DEFAULT 0,
  `grow_exp` int(11) NOT NULL DEFAULT 0,
  `special_kind` int(11) NOT NULL DEFAULT 0,
  `stone_attr` int(11) NOT NULL DEFAULT 0,
  `stone_level` int(11) NOT NULL DEFAULT 0,
  `enhance_level` int(11) NOT NULL DEFAULT 0,
  `delete_time` double NOT NULL DEFAULT 0,
  `damaged_item_id` int(11) NOT NULL DEFAULT 0,
  `is_locked` tinyint(1) NOT NULL DEFAULT 0,
  `reinforced` int(11) NOT NULL DEFAULT 0,
  `affix1` int(11) NOT NULL DEFAULT 0,
  `affix2` int(11) NOT NULL DEFAULT 0,
  `affix3` int(11) NOT NULL DEFAULT 0,
  `style_level` int(11) NOT NULL DEFAULT 0,
  `npc_data` blob DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

--
-- Indexes for dumped tables
--

--
-- Indexes for table `accounts`
--
ALTER TABLE `accounts`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `accounts_account_unique` (`account`);

--
-- Indexes for table `characters`
--
ALTER TABLE `characters`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `characters_name_unique` (`name`),
  ADD KEY `characters_account_id` (`account_id`);

--
-- Indexes for table `character_bit_flags`
--
ALTER TABLE `character_bit_flags`
  ADD PRIMARY KEY (`char_id`,`flag_id`);

--
-- Indexes for table `character_completed_events`
--
ALTER TABLE `character_completed_events`
  ADD PRIMARY KEY (`char_id`,`map_id`,`eve_no`);

--
-- Indexes for table `character_dispatches`
--
ALTER TABLE `character_dispatches`
  ADD PRIMARY KEY (`id`),
  ADD KEY `character_dispatches_character_id` (`character_id`);

--
-- Indexes for table `character_fly_points`
--
ALTER TABLE `character_fly_points`
  ADD PRIMARY KEY (`char_id`,`point_index`),
  ADD KEY `character_fly_points_char_id` (`char_id`);

--
-- Indexes for table `character_hotkeys`
--
ALTER TABLE `character_hotkeys`
  ADD PRIMARY KEY (`id`),
  ADD KEY `character_hotkeys_character_id` (`character_id`);

--
-- Indexes for table `character_missions`
--
ALTER TABLE `character_missions`
  ADD PRIMARY KEY (`char_id`,`mission_id`);

--
-- Indexes for table `character_mission_flags`
--
ALTER TABLE `character_mission_flags`
  ADD PRIMARY KEY (`char_id`,`bit_index`);

--
-- Indexes for table `character_money`
--
ALTER TABLE `character_money`
  ADD PRIMARY KEY (`character_id`);

--
-- Indexes for table `character_mounts`
--
ALTER TABLE `character_mounts`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `character_mounts_character_id_unique` (`character_id`);

--
-- Indexes for table `character_mount_equips`
--
ALTER TABLE `character_mount_equips`
  ADD PRIMARY KEY (`id`),
  ADD KEY `character_mount_equips_character_id` (`character_id`);

--
-- Indexes for table `character_pets`
--
ALTER TABLE `character_pets`
  ADD PRIMARY KEY (`id`),
  ADD KEY `character_pets_character_id` (`character_id`);

--
-- Indexes for table `character_pet_equips`
--
ALTER TABLE `character_pet_equips`
  ADD PRIMARY KEY (`id`),
  ADD KEY `character_pet_equips_character_id` (`character_id`);

--
-- Indexes for table `character_role_counts`
--
ALTER TABLE `character_role_counts`
  ADD PRIMARY KEY (`char_id`,`count_index`);

--
-- Indexes for table `character_skills`
--
ALTER TABLE `character_skills`
  ADD PRIMARY KEY (`character_id`,`skill_id`),
  ADD KEY `character_skills_character_id` (`character_id`);

--
-- Indexes for table `character_ui_sell_counts`
--
ALTER TABLE `character_ui_sell_counts`
  ADD PRIMARY KEY (`char_id`,`sell_id`),
  ADD KEY `character_ui_sell_counts_char_id` (`char_id`);

--
-- Indexes for table `friends`
--
ALTER TABLE `friends`
  ADD PRIMARY KEY (`char_id`,`friend_id`),
  ADD KEY `friends_char_id` (`char_id`),
  ADD KEY `friends_friend_id` (`friend_id`);

--
-- Indexes for table `friend_invites`
--
ALTER TABLE `friend_invites`
  ADD PRIMARY KEY (`id`),
  ADD KEY `friend_invites_target_char_id` (`target_char_id`);

--
-- Indexes for table `inventories`
--
ALTER TABLE `inventories`
  ADD PRIMARY KEY (`character_id`,`storage_type`,`slot`),
  ADD KEY `inventories_character_id` (`character_id`);

--
-- Indexes for table `mails`
--
ALTER TABLE `mails`
  ADD PRIMARY KEY (`id`),
  ADD KEY `mails_char_id` (`char_id`);

--
-- Indexes for table `mail_attachments`
--
ALTER TABLE `mail_attachments`
  ADD PRIMARY KEY (`mail_id`,`slot`),
  ADD KEY `mail_attachments_mail_id` (`mail_id`);

--
-- AUTO_INCREMENT for dumped tables
--

--
-- AUTO_INCREMENT for table `accounts`
--
ALTER TABLE `accounts`
  MODIFY `id` bigint(20) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=2;

--
-- AUTO_INCREMENT for table `characters`
--
ALTER TABLE `characters`
  MODIFY `id` bigint(20) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=2;

--
-- AUTO_INCREMENT for table `character_dispatches`
--
ALTER TABLE `character_dispatches`
  MODIFY `id` bigint(20) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT for table `character_hotkeys`
--
ALTER TABLE `character_hotkeys`
  MODIFY `id` bigint(20) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT for table `character_mounts`
--
ALTER TABLE `character_mounts`
  MODIFY `id` bigint(20) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=2;

--
-- AUTO_INCREMENT for table `character_mount_equips`
--
ALTER TABLE `character_mount_equips`
  MODIFY `id` bigint(20) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT for table `character_pets`
--
ALTER TABLE `character_pets`
  MODIFY `id` bigint(20) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=2;

--
-- AUTO_INCREMENT for table `character_pet_equips`
--
ALTER TABLE `character_pet_equips`
  MODIFY `id` bigint(20) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT for table `friend_invites`
--
ALTER TABLE `friend_invites`
  MODIFY `id` bigint(20) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT for table `mails`
--
ALTER TABLE `mails`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;
COMMIT;

/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
