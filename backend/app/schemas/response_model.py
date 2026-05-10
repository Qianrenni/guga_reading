import enum
from typing import Generic, TypeVar

from pydantic import BaseModel


class ResponseCode(enum.IntEnum):
    SUCCESS = 0
    ERROR = 1


T = TypeVar("T")


class ResponseModel(BaseModel, Generic[T]):
    """
    响应模型
    """

    code: ResponseCode = ResponseCode.SUCCESS
    message: str = ""
    data: T | None = None
