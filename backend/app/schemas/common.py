# schemas/common.py

from pydantic import BaseModel


class CountResponseModel(BaseModel):
    count: int


class CodeResponseModel(BaseModel):
    code: str
