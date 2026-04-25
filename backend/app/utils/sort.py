from app.models.sql.book import Book


class Reverse:
    __slots__ = ("obj",)

    def __init__(self, obj):
        self.obj = obj

    def __lt__(self, other):
        return self.obj > other.obj

    def __gt__(self, other):
        return self.obj < other.obj

    def __eq__(self, other):
        return self.obj == other.obj

    def __le__(self, other):
        return self.obj >= other.obj

    def __ge__(self, other):
        return self.obj <= other.obj

    def __ne__(self, other):
        return self.obj != other.obj


class SortItem:
    def __init__(self, field: str, order: int):
        self.field = field
        self.order = order

    @classmethod
    def from_str(cls, s: str) -> "SortItem":
        if ":" not in s:
            raise ValueError("sort 格式应为 'field:order',如 'rating:-1'")
        field, order_str = s.split(":", 1)
        if field not in Book.sort_fields():
            raise ValueError(f"不支持排序字段: {field}")
        order = int(order_str)
        if order not in {1, -1}:
            raise ValueError("排序方向只能是 1或 -1")
        return cls(field, order)
