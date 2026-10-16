USE shiguang;

CREATE TABLE IF NOT EXISTS `counter_batch_receipt` (
    `counter_name` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `batch_id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `applied_at` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (`counter_name`, `batch_id`)
) ENGINE=InnoDB COMMENT='Buffered counter batches committed with their increments; retain for replay protection';
