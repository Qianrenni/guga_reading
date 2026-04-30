# enums.py
from enum import Enum


class ResourceTypeEnum(Enum):
    BOOK = "book"  # 书籍
    USER = "user"  # 管理用户
    PERMISSION = "permission"  # 管理权限


class ActionEnum(Enum):
    # 标准 CRUD
    READ = "read"
    CREATE = "create"
    UPDATE = "update"
    DELETE = "delete"
    AUDIT = "audit"  # 审核内容(通过/拒绝)
    MANAGE = "manage"  # 管理内容
    # 业务扩展动作
    # PUBLISH = "publish"  # 发布书籍/章节
    # BAN = "ban"  # 封禁用户/评论
    # RESTORE = "restore"  # 恢复被删内容
    # COLLECT = "collect"  # 收藏(可归入 CREATE,但语义更清)
    # LIKE = "like"  # 点赞(高频,可单独控制)
    # IMPERSONATE = "impersonate"  # 模拟登录(管理员)

    # # 支付相关(未来)
    # REFUND = "refund"
    # WITHDRAW = "withdraw"


class ScopeEnum(Enum):
    OWN = "own"  # 仅自己的数据(如:只能删自己的评论)
    ALL = "all"  # 全局数据(如:管理员删任意用户)
    # TEAM = "team"  # 团队内数据(如:编辑组可管理本组书籍)→ **可选,初期可不用**


class RoleEnum(Enum):
    USER = "user"  # 普通用户
    REVIEWER = "reviewer"  # 内容审核
    AUTHOR = "author"  # 作者
    ADMIN = "admin"  # 系统管理员
    SUPER_ADMIN = "super_admin"  # 超级管理员


class BookStatusEnum(Enum):
    # 待处理
    PENDING = "pending"
    # 提交审核
    REVIEWING = "reviewing"
    # 审核通过
    APPROVED = "approved"
    # 审核拒绝
    REJECTED = "rejected"
    # 发布
    PUBLISHED = "published"


class ReportEnum(Enum):
    """
    报告类型
    Args:
        ENTER (str): 进入
        EXIT (str): 离开
        HEATBEAT (str): 心跳
    """

    # 进入
    ENTER = "enter"
    # 离开
    EXIT = "exit"
    # 心跳
    HEATBEAT = "heartbeat"
