CREATE
DATABASE if NOT EXISTS `ktor`;

USE
`ktor`;

CREATE TABLE if not EXISTS `user`
(
    `id`
    int
    NOT
    NULL
    AUTO_INCREMENT,
    `userName`
    varchar
(
    255
) NOT NULL,
    `email` varchar
(
    255
) NOT NULL,
    `password` varchar
(
    255
) NOT NULL,
    `isActive` tinyint
(
    1
) NOT NULL DEFAULT '1',
    `avatar` varchar
(
    255
) NOT NULL DEFAULT '',
    `createdAt` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updatedAt` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY
(
    `id`
)
    ) ENGINE = InnoDB AUTO_INCREMENT = 2 DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE if not EXISTS `permission`
(
    `id`
    int
    NOT
    NULL
    AUTO_INCREMENT,
    `name`
    varchar
(
    100
) NOT NULL,
    `resourceType` enum
(
    'BOOK',
    'USER',
    'PERMISSION'
) NOT NULL,
    `action` enum
(
    'READ',
    'CREATE',
    'UPDATE',
    'DELETE',
    'AUDIT',
    'MANAGE'
) NOT NULL,
    `scope` enum
(
    'OWN',
    'ALL'
) NOT NULL,
    `bitPosition` int NOT NULL,
    `createdAt` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updatedAt` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY
(
    `id`
),
    UNIQUE KEY `uk_permission_name`
(
    `name`
),
    UNIQUE KEY `uk_permission_bitPosition`
(
    `bitPosition`
),
    UNIQUE KEY `index_unique_code`
(
    `resourceType`,
    `action`,
    `scope`
)
    ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE if not EXISTS `role`
(
    `id`
    int
    NOT
    NULL
    AUTO_INCREMENT,
    `name`
    varchar
(
    50
) NOT NULL,
    `code` enum
(
    'USER',
    'REVIEWER',
    'AUTHOR',
    'ADMIN',
    'SUPER_ADMIN'
) NOT NULL,
    `description` varchar
(
    255
) DEFAULT NULL,
    `createdAt` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updatedAt` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY
(
    `id`
),
    UNIQUE KEY `uk_role_name`
(
    `name`
),
    UNIQUE KEY `uk_role_code`
(
    `code`
)
    ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE if not EXISTS `role_inheritance`
(
    `childId`
    int
    NOT
    NULL,
    `parentId`
    int
    NOT
    NULL,
    `createdAt`
    datetime
    NOT
    NULL
    DEFAULT
    CURRENT_TIMESTAMP,
    PRIMARY
    KEY
(
    `childId`,
    `parentId`
),
    KEY `fk_role_inheritance_parent`
(
    `parentId`
),
    CONSTRAINT `fk_role_inheritance_child` FOREIGN KEY
(
    `childId`
) REFERENCES `role`
(
    `id`
),
    CONSTRAINT `fk_role_inheritance_parent` FOREIGN KEY
(
    `parentId`
) REFERENCES `role`
(
    `id`
),
    CONSTRAINT `child_id_not_equal_parent_id` CHECK
(
    `childId`
    <>
    `parentId`
)
    ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE if not EXISTS `role_permission`
(
    `roleId`
    int
    NOT
    NULL,
    `permissionId`
    int
    NOT
    NULL,
    `createdAt`
    datetime
    NOT
    NULL
    DEFAULT
    CURRENT_TIMESTAMP,
    PRIMARY
    KEY
(
    `roleId`,
    `permissionId`
),
    KEY `fk_role_permission_permission`
(
    `permissionId`
),
    CONSTRAINT `fk_role_permission_role` FOREIGN KEY
(
    `roleId`
) REFERENCES `role`
(
    `id`
),
    CONSTRAINT `fk_role_permission_permission` FOREIGN KEY
(
    `permissionId`
) REFERENCES `permission`
(
    `id`
)
    ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE if not EXISTS `user_role`
(
    `userId`
    int
    NOT
    NULL,
    `roleId`
    int
    NOT
    NULL,
    `grantedBy`
    int
    DEFAULT
    NULL,
    `grantedAt`
    datetime
    NOT
    NULL
    DEFAULT
    CURRENT_TIMESTAMP,
    PRIMARY
    KEY
(
    `userId`,
    `roleId`
),
    KEY `fk_user_role_role`
(
    `roleId`
),
    KEY `fk_user_role_granted_by`
(
    `grantedBy`
),
    CONSTRAINT `fk_user_role_user` FOREIGN KEY
(
    `userId`
) REFERENCES `user`
(
    `id`
),
    CONSTRAINT `fk_user_role_role` FOREIGN KEY
(
    `roleId`
) REFERENCES `role`
(
    `id`
),
    CONSTRAINT `fk_user_role_granted_by` FOREIGN KEY
(
    `grantedBy`
) REFERENCES `user`
(
    `id`
)
    ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

INSERT INTO `permission` (`name`,
                          `resourceType`,
                          `action`,
                          `scope`,
                          `bitPosition`,
                          `createdAt`,
                          `updatedAt`)
SELECT `name`,
       `resource_type`,
       `action`,
       `scope`,
       `bit_position`,
       `created_at`,
       `updated_at`
FROM `beta`.`permission`;

INSERT INTO `role` (`name`,
                    `code`,
                    `description`,
                    `createdAt`,
                    `updatedAt`)
SELECT `name`,
       `code`,
       `description`,
       `created_at`,
       `updated_at`
FROM `beta`.`role`;

insert into `role_inheritance` (`childId`,
                                `parentId`,
                                `createdAt`)
select `child_id`,
       `parent_id`,
       `created_at`
from `beta`.`role_inheritance`;

INSERT INTO `role_permission` (`roleId`,
                               `permissionId`,
                               `createdAt`)
SELECT `role_id`,
       `permission_id`,
       `created_at`
FROM `beta`.`role_permission`;