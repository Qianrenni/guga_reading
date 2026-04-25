from typing import Annotated

from fastapi import APIRouter, Depends, Header

from app.enum.enum import ActionEnum, ResourceTypeEnum, ScopeEnum
from app.models.response_model import ResponseModel
from app.models.sql.right import Permission, Role
from app.models.sql.user import FullUser
from app.services.right_service import (
    RightService,
    generate_permission_code,
    right_check,
)

right_router = APIRouter(prefix="/right", tags=["right"])


@right_router.get("/permission", response_model=ResponseModel[list[Permission]])
async def get_permission(
    Authorization: Annotated[str, Header(name="Authorization")],
    _: Annotated[
        FullUser,
        Depends(
            right_check(
                [
                    generate_permission_code(
                        resource=ResourceTypeEnum.PERMISSION,
                        action=ActionEnum.READ,
                        scope=ScopeEnum.ALL,
                    )
                ]
            )
        ),
    ],
):
    result = await RightService.permission()
    return ResponseModel[list[Permission]](data=result)


@right_router.get("/role", response_model=ResponseModel[list[Role]])
async def get_role():
    result = await RightService.role()
    return ResponseModel[list[Role]](data=result)


@right_router.get(
    "/role_permission",
    response_model=ResponseModel[list[tuple[int, list[int]]]],
    description="返回角色权限关系",
)
async def get_role_permission():
    result = await RightService.role_permission()
    return ResponseModel[list[tuple[int, list[int]]]](data=result)
