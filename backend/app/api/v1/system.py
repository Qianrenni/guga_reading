from typing import Annotated

from fastapi import APIRouter, Depends

from app.enum.enum import ActionEnum, ResourceTypeEnum, ScopeEnum
from app.models.domain.system import SystemStatus
from app.models.domain.user import FullUser
from app.schemas.response_model import ResponseModel
from app.services.right_service import generate_permission_code, right_check
from app.services.system_service import SystemService

system_router = APIRouter(prefix="/system", tags=["system"])


@system_router.get("/info", response_model=ResponseModel[SystemStatus])
async def system_info(
    _current_user: Annotated[
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
    result = await SystemService.get_system_info()
    return ResponseModel[SystemStatus](data=result)
