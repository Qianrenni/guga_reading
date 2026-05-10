from pydantic import BaseModel


class BookCatalogItemResponseModel(BaseModel):
    """
    Attributes:
        id: 章节ID
        title: 章节标题
    """

    id: int
    title: str
