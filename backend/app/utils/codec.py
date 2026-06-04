# cache/base.py
import json
from abc import ABC, abstractmethod
from typing import Any

from pydantic import BaseModel, TypeAdapter


class CodeC(ABC):
    """
    编解码器抽象基类
    """

    @abstractmethod
    def encode(self, value: Any) -> str:
        """
        将值序列化为字符串(用于存入缓存)

        @param value: 需要序列化的任意类型值
        @return str: 序列化后的字符串
        """
        pass

    @abstractmethod
    def decode(self, data: str) -> Any:
        """
        从字符串反序列化为原始值(用于从缓存读取)

        @param data: 需要反序列化的字符串
        @return Any: 反序列化后的原始值
        """
        pass


class CacheCodec(CodeC):
    """缓存编解码器,使用JSON格式序列化"""

    def encode(self, value: Any) -> str:
        """
        将值序列化为JSON字符串

        @param value: 需要序列化的任意类型值
        @return str: JSON格式的字符串
        """
        return json.dumps(value, default=str)

    def decode(self, data: str) -> Any:
        """
        从JSON字符串反序列化为原始值

        @param data: JSON格式的字符串
        @return Any: 反序列化后的原始值
        """
        return json.loads(data)


class PydanticCodec(CacheCodec):
    """Pydantic 模型专用编解码器"""

    def __init__(self, model_cls):
        """
        初始化Pydantic编解码器

        @param model_cls: Pydantic模型类,必须是BaseModel的子类
        @raise ValueError: 当model_cls不是BaseModel子类时抛出
        """
        if not issubclass(model_cls, BaseModel):
            raise ValueError("model_cls 必须是 pydantic.BaseModel 的子类")
        self.model_cls = model_cls

    def encode(self, value: BaseModel) -> str:
        """
        将Pydantic模型序列化为JSON字符串

        @param value: Pydantic模型实例
        @return str: JSON格式的字符串
        """
        return value.model_dump_json()

    def decode(self, data: str) -> BaseModel:
        """
        从JSON字符串反序列化为Pydantic模型

        @param data: JSON格式的字符串
        @return BaseModel: Pydantic模型实例
        """
        return self.model_cls.model_validate_json(data)


class PydanticListCodec(CacheCodec):
    """Pydantic 模型列表专用编解码器"""

    def __init__(self, model_cls):
        """
        初始化Pydantic列表编解码器

        @param model_cls: Pydantic模型类,必须是BaseModel的子类
        @raise ValueError: 当model_cls不是BaseModel子类时抛出
        """
        if not issubclass(model_cls, BaseModel):
            raise ValueError("model_cls 必须是 pydantic.BaseModel 的子类")
        self.model_cls = model_cls
        self.adapter = TypeAdapter(list[model_cls])

    def encode(self, value: list[BaseModel]) -> str:
        """
        将Pydantic模型列表序列化为JSON字符串

        @param value: Pydantic模型实例列表
        @return str: JSON格式的字符串
        """
        return self.adapter.dump_json(value).decode("utf-8")

    def decode(self, data: str) -> list[BaseModel]:
        """
        从JSON字符串反序列化为Pydantic模型列表

        @param data: JSON格式的字符串
        @return list[BaseModel]: Pydantic模型实例列表
        """
        return self.adapter.validate_json(data)
