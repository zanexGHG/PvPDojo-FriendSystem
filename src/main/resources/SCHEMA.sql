CREATE TABLE IF NOT EXISTS `fs_players` (
  `uuid` CHAR(36) NOT NULL,
  `last_seen` TIMESTAMP NULL DEFAULT NULL,
  PRIMARY KEY (`uuid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `fs_relations` (
  `uuid_a` CHAR(36) NOT NULL,
  `uuid_b` CHAR(36) NOT NULL,
  `relation` TINYINT NOT NULL,
  `last_changed` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`uuid_a`, `uuid_b`),
  KEY `idx_uuid_a` (`uuid_a`),
  KEY `idx_uuid_b` (`uuid_b`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `fs_requests` (
  `uuid_from` CHAR(36) NOT NULL,
  `uuid_to` CHAR(36) NOT NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`uuid_from`, `uuid_to`),
  KEY `idx_to` (`uuid_to`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
