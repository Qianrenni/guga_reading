# cache/base.py
import json
from abc import ABC, abstractmethod
from typing import Any

from pydantic import BaseModel, TypeAdapter


class CodeC(ABC):
    """
    编解码器
    """

    @abstractmethod
    def encode(self, value: Any) -> str:
        """将值序列化为字节(存入 Redis)"""
        pass

    @abstractmethod
    def decode(self, data: str) -> Any:
        """从字节反序列化为值(从 Redis 读取)"""
        pass


class CacheCodec(CodeC):
    """缓存编解码器"""

    def encode(self, value: Any) -> str:
        """
        Args:
              value Any
        return str
        """
        return json.dumps(value, default=str)

    def decode(self, data: str) -> Any:
        """
        Args:
              data str
        return Any
        """
        return json.loads(data)


class PydanticCodec(CacheCodec):
    """Pydantic 模型专用编解码器"""

    def __init__(self, model_cls):
        if not issubclass(model_cls, BaseModel):
            raise ValueError("model_cls 必须是 pydantic.BaseModel 的子类")
        self.model_cls = model_cls

    def encode(self, value: BaseModel) -> str:
        return value.model_dump_json()

    def decode(self, data: str) -> BaseModel:
        return self.model_cls.model_validate_json(data)


class PydanticListCodec(CacheCodec):
    """Pydantic 模型列表专用编解码器"""

    def __init__(self, model_cls):
        if not issubclass(model_cls, BaseModel):
            raise ValueError("model_cls 必须是 pydantic.BaseModel 的子类")
        self.model_cls = model_cls
        self.adapter = TypeAdapter(list[model_cls])

    def encode(self, value: list[BaseModel]) -> str:
        return self.adapter.dump_json(value).decode("utf-8")

    def decode(self, data: str) -> list[BaseModel]:
        return self.adapter.validate_json(data)
