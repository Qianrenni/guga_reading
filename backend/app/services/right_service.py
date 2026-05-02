from typing import Annotated

from fastapi import Depends, status
from sqlmodel import select
from sqlmodel.ext.asyncio.session import AsyncSession

from app.core.config import SETTING
from app.core.database import get_session_context
from app.core.error_handler import AppError
from app.core.security import get_current_user
from app.enum.enum import ActionEnum, ResourceTypeEnum, ScopeEnum
from app.middleware.logging import logger
from app.models.sql.right import (
    Permission,
    Role,
    RoleInheritance,
    RolePermission,
    UserRole,
)
from app.models.sql.user import FullUser
from app.services.cache_service import cache
from app.utils.codec import PydanticListCodec


class RightService:
    """
    权限服务类:用于预加载权限数据、构建角色权限位图,并提供高效的权限校验能力。

    设计说明:
    - 权限以位图形式存储(分段 list[int]),每段长度由 SETTING.PERMISSION_BIT_LENGTH 控制(通常为 64)。
    - 用户权限位图会序列化到 JWT 中,服务端通过位运算高效校验,无需每次查数据库。
    - 所有数据在应用启动时通过 prepare_info() 预加载到内存,后续操作均为 O(1) 或 O(n)(n 为权限段数)。
    """

    # 权限 ID 到 Permission 对象的映射
    permission_dict: dict[int, Permission] = {}  # noqa: RUF012

    # 权限编码(如 "admin:user:read:all")到 bit_position 的映射,用于快速查找
    permission_code_map: dict[str, int] = {}  # noqa: RUF012

    # 角色 ID 到 Role 对象的映射
    role_dict: dict[int, Role] = {}  # noqa: RUF012

    # role_inheritance:
    role_inheritance_dict: dict[int, list[int]] = {}  # noqa: RUF012

    # 角色权限位图:role_id -> [segment0, segment1, ...]
    # 每个 segment 是一个整数,每一位代表一个权限(1 表示拥有,0 表示无)
    role_permission_dict: dict[int, list[int]] = {}  # noqa: RUF012

    @staticmethod
    def _set_bits_in_bitmap(
        bit_positions: list[int], bitmap_segments: list[int]
    ) -> list[int]:
        """
        将一组 bit_position 设置到位图段列表中(原地修改 bitmap_segments)。

        位图分段说明:
        - 若 PERMISSION_BIT_LENGTH = 64,则:
          - bit_position 0~63 → segment[0]
          - bit_position 64~127 → segment[1]
          - ...
        - 例如:bit_position=70 → segment_index=1, offset=6 → segment[1] |= (1 << 6)

        :param bit_positions: 要设置的权限位位置列表(如 [7, 70])
        :param bitmap_segments: 当前位图段列表(如 [0b101, 0b000]),函数会原地扩展并修改
        :return: 修改后的 bitmap_segments(与输入为同一对象)
        """
        for bit_pos in bit_positions:
            segment_index, offset = divmod(bit_pos, SETTING.PERMISSION_BIT_LENGTH)
            needed_segments = segment_index + 1

            # 如果当前段数不足,扩展至 needed_segments
            if len(bitmap_segments) < needed_segments:
                bitmap_segments.extend([0] * (needed_segments - len(bitmap_segments)))

            # 在对应段中设置该位
            bitmap_segments[segment_index] |= 1 << offset

        return bitmap_segments

    @staticmethod
    def merge_role_permission(
        parents_segments: list[list[int]], child_segments: list[int]
    ):
        """
        合并多个父角色的权限位图和子角色的权限位图。

        :param parents_segments: 父角色的权限位图列表
        :param child_segments: 子角色的权限位图列表
        :return: 合并后的权限位图列表
        """
        for parent_segment in parents_segments:
            if len(parent_segment) > len(child_segments):
                child_segments.extend([0] * (len(parent_segment) - len(child_segments)))
            for index, index_segment in enumerate(parent_segment):
                child_segments[index] |= index_segment

    @staticmethod
    def flatten_inherit_role(
        role_inheritance_list: list[RoleInheritance],
        role_permission_dict: dict[int, list[int]],
    ) -> dict[int, list[int]]:
        """
        Role表结构有继承父子关系所以需要递归展开
        递归构建角色权限位图。

        :param roles_inheritance: 要处理的角色列表
        :return: 当前role_id继承了哪些角色
        """
        from collections import defaultdict, deque

        # === 1. 只构建必要的数据结构 ===
        # child -> parents(必须)
        child_to_parents = defaultdict(list)
        # parent -> children(必须,用于拓扑推进)
        parent_to_children = defaultdict(list)
        # 入度计数(用 defaultdict(int) 更高效)
        in_degree = defaultdict(int)

        # 收集所有角色(用 set,但后续转为 list 避免重复 hash)
        all_roles = set()

        for rel in role_inheritance_list:
            c, p = rel.child_id, rel.parent_id
            child_to_parents[c].append(p)
            parent_to_children[p].append(c)
            in_degree[c] += 1
            all_roles.add(c)
            all_roles.add(p)

        # 转为列表,避免后续多次 hash
        all_roles = list(all_roles)

        # === 2. 初始化:只分配必要内存 ===
        # 用列表代替字典存储 result,角色 ID 映射到 index(如果 ID 连续且密集)
        # 但 ID 可能稀疏(如 1, 1000000),所以仍用 dict,但 value 用 list + 手动去重
        result: dict[int, list[int]] = {}

        # 入度为 0 的角色(根)
        queue = deque()
        for role in all_roles:
            if in_degree[role] == 0:
                queue.append(role)
                result[role] = [role]  # 直接用 list,避免 set 转换

        # === 3. 拓扑排序 + 手动去重(避免 set overhead)===
        while queue:
            role = queue.popleft()
            ancestors = result[role]  # 自身已包含

            # 聚合所有父角色的祖先
            for parent in child_to_parents.get(role, []):
                parent_ancestors = result[parent]  # 父的结果已就绪
                ancestors.extend(parent_ancestors)

            # 手动去重(保留顺序,且避免 set 开销)
            # 使用 dict.fromkeys() 保持插入顺序(Python 3.7+)
            result[role] = list(dict.fromkeys(ancestors))
            parent_segments = role_permission_dict[role]
            # 推进子节点
            for child in parent_to_children.get(role, []):
                in_degree[child] -= 1
                if in_degree[child] == 0:
                    queue.append(child)
                    result[child] = [child]  # 预分配
                child_segments = role_permission_dict[child]
                RightService.merge_role_permission([parent_segments], child_segments)
        return result

    @classmethod
    async def prepare_info(cls):
        """
        应用启动时调用:预加载所有权限、角色及角色-权限关联数据到内存。

        执行步骤:
        1. 加载所有 Permission、Role、RolePermission 表数据。
        2. 构建 permission_dict 和 permission_code_map。
        3. 为每个角色构建分段权限位图(role_permission_dict)。

        注意:
        - 无效的 RolePermission(如 permission_id 不存在)会被跳过。
        - 位图按 bit_position 自动分段,支持任意数量的权限。
        """
        logger.info("正在预加载权限、角色及角色-权限关联数据...")
        async with get_session_context() as session:
            # 并行查询三张表
            select_permissions = select(Permission).order_by(Permission.bit_position)
            select_roles = select(Role).order_by(Role.id)
            select_role_permissions = select(RolePermission)
            select_role_inheritance = select(RoleInheritance)

            perm_result = await session.exec(select_permissions)
            role_result = await session.exec(select_roles)
            rp_result = await session.exec(select_role_permissions)
            role_inheritance_result = await session.exec(select_role_inheritance)
            # 构建权限字典
            permissions = perm_result.all()
            cls.permission_dict = {p.id: p for p in permissions}
            cls.permission_code_map = {
                f"{p.resource_type}:{p.action}:{p.scope}": p.bit_position
                for p in permissions
            }

            # 构建角色字典
            cls.role_dict = {r.id: r for r in role_result.all()}
            # 构建角色权限位图
            cls.role_permission_dict.clear()
            for rp in rp_result.all():
                perm = cls.permission_dict.get(rp.permission_id)
                if perm is None:
                    continue  # 跳过无效权限关联(可记录日志)

                role_id = rp.role_id
                bit_pos = perm.bit_position

                # 获取或初始化该角色的位图段列表
                if role_id not in cls.role_permission_dict:
                    cls.role_permission_dict[role_id] = []
                segments = cls.role_permission_dict[role_id]

                # 将当前权限位设置到位图中
                RightService._set_bits_in_bitmap([bit_pos], segments)
            # 构建角色继承关系字典
            cls.role_inheritance_dict = cls.flatten_inherit_role(
                list(role_inheritance_result.all()), cls.role_permission_dict
            )
            logger.info("权限信息加载完成")
            logger.debug(cls.role_permission_dict)

    @classmethod
    def get_merged_permission_bitmap(cls, role_ids: list[int]) -> list[int]:
        """
        根据用户的角色 ID 列表,合并生成用户的完整权限位图。

        合并规则:按位 OR(只要任一角色拥有该权限,用户就拥有)。

        :param role_ids: 用户拥有的角色 ID 列表
        :return: 合并后的权限位图,格式为 [segment0, segment1, ...]
        """
        if not role_ids:
            return []

        merged_bitmap = []
        RightService.merge_role_permission(
            parents_segments=[
                cls.role_permission_dict[role_id] for role_id in role_ids
            ],
            child_segments=merged_bitmap,
        )

        return merged_bitmap

    @classmethod
    def check_permission(
        cls, required_permission_codes: list[str], user_permission_bitmap: list[int]
    ) -> bool:
        """
        校验用户是否拥有所有指定的权限。

        流程:
        1. 将 required_permission_codes 转换为 bit_position 列表。
        2. 构建所需的权限位图(require_bitmap)。
        3. 与用户权限位图(user_permission_bitmap)按段比较:
           - (user_segment & require_segment) == require_segment 表示权限满足。

        安全说明:
        - user_permission_bitmap 应来自可信 JWT payload(已签名,不可伪造)。
        - 若 required_permission_codes 中包含未知权限,直接返回 False。

        :param required_permission_codes: 所需权限编码列表,如 ["admin:user:read:all"]
        :param user_permission_bitmap: 用户权限位图(来自 JWT),如 [0b10101, 0b11101]
        :return: True 表示用户拥有全部所需权限,否则 False
        """
        # 1. 转换权限编码为 bit_position
        required_bits = []
        for code in required_permission_codes:
            bit_pos = cls.permission_code_map.get(code)
            if bit_pos is None:
                return False  # 未知权限,拒绝访问
            required_bits.append(bit_pos)

        # 2. 构建所需的权限位图
        require_bitmap = RightService._set_bits_in_bitmap(required_bits, [])

        # 3. 安全校验:不修改输入的 user_permission_bitmap
        # 对齐长度:若 user_bitmap 段数不足,视为高位为 0
        user_bitmap_padded = user_permission_bitmap + [0] * max(
            0, len(require_bitmap) - len(user_permission_bitmap)
        )

        # 4. 逐段检查是否满足所有权限
        for req_seg, user_seg in zip(require_bitmap, user_bitmap_padded, strict=True):
            if (user_seg & req_seg) != req_seg:
                return False
        return True

    @classmethod
    async def add_user_role(cls, user_id: int, role_code: str, database: AsyncSession):
        """
        为用户添加角色
        :param user_id: 用户ID
        :param role_code: 角色编码
        :param database: 数据库会话
        :return: 是否添加成功
        :raise: 异常
        """
        for role in cls.role_dict.values():
            if role.code == role_code:
                user_role = UserRole(user_id=user_id, role_id=role.id)
                database.add(user_role)
                return True
        return False

    @classmethod
    async def get_user_role(cls, user_id: int, database: AsyncSession):
        """
        获取用户角色
        :param user_id: 用户ID
        :param database: 数据库会话
        :return: 用户角色列表
        :raise: 异常
        """
        result = await database.exec(
            select(UserRole).where(UserRole.user_id == user_id)
        )
        return list(result.all())

    @classmethod
    @cache(expire=SETTING.PERMISSION_CACHE_EXPIRE, codec=PydanticListCodec(Permission))
    async def permission(cls) -> list[Permission]:
        """
        权限列表缓存
        """
        return list(cls.permission_dict.values())

    @classmethod
    @cache(expire=SETTING.PERMISSION_CACHE_EXPIRE, codec=PydanticListCodec(Role))
    async def role(cls) -> list[Role]:
        """
        角色列表缓存
        """
        return list(cls.role_dict.values())

    @classmethod
    @cache(expire=SETTING.PERMISSION_CACHE_EXPIRE)
    async def role_permission(cls) -> list[tuple[int, list[int]]]:
        """
        角色权限缓存
        """
        return list(cls.role_permission_dict.items())


def generate_permission_code(
    resource: ResourceTypeEnum, action: ActionEnum, scope: ScopeEnum
) -> str:
    """
    生成权限编码
    :param resource: 资源
    :param action: 动作
    :param scope: 范围
    :return: 权限编码
    """
    if resource not in ResourceTypeEnum.__members__.values():
        raise ValueError("Invalid resource")
    if action not in ActionEnum.__members__.values():
        raise ValueError("Invalid action")
    if scope not in ScopeEnum.__members__.values():
        raise ValueError("Invalid scope")
    return f"{resource.value}:{action.value}:{scope.value}"


def right_check(permission_codes: list[str]):
    """
    权限检查装饰器
    :param permission_codes: 权限编码列表
    :return: 无
    """

    def helper(current_user: Annotated[FullUser, Depends(get_current_user)]):
        """
        权限检查函数
        """
        is_pass = RightService.check_permission(permission_codes, current_user.right)
        if not is_pass:
            raise AppError(
                status_code=status.HTTP_403_FORBIDDEN,
                message="权限不足",
            )
        return current_user

    return helper
