from pydantic import BaseModel

from app.models.domain.user import FullUser
from app.schemas.response_model import ResponseModel


class TokenData(BaseModel):
    access_token: str
    refresh_token: str
    token_type: str = "Bearer"
    user: FullUser


class Token(ResponseModel):
    """
    令牌模型
    - data
    - - access_token (str): 访问令牌
    - - token_type (str): 令牌类型
    """

    data: TokenData | None = None
