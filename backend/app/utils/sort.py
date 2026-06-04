from app.models.database.book import Book


class Reverse:
    """
    排序包装器,用于实现降序排序
    通过反转比较运算符实现降序效果
    """

    __slots__ = ("obj",)

    def __init__(self, obj):
        """
        初始化Reverse包装器

        @param obj: 需要降序排序的对象
        """
        self.obj = obj

    def __lt__(self, other):
        """小于比较,反转为大于比较以实现降序"""
        return self.obj > other.obj

    def __gt__(self, other):
        """大于比较,反转为小于比较以实现降序"""
        return self.obj < other.obj

    def __eq__(self, other):
        """等于比较"""
        return self.obj == other.obj

    def __le__(self, other):
        """小于等于比较,反转为大于等于比较"""
        return self.obj >= other.obj

    def __ge__(self, other):
        """大于等于比较,反转为小于等于比较"""
        return self.obj <= other.obj

    def __ne__(self, other):
        """不等于比较"""
        return self.obj != other.obj


class SortItem:
    """排序项,表示单个字段的排序规则"""

    def __init__(self, field: str, order: int):
        """
        初始化排序项

        @param field: 排序字段名
        @param order: 排序方向,1表示升序,-1表示降序
        """
        self.field = field
        self.order = order

    @classmethod
    def from_str(cls, s: str) -> "SortItem":
        """
        从字符串创建排序项

        @param s: 格式为'field:order'的字符串,如'rating:-1'
        @return SortItem: 排序项实例
        @raise ValueError: 当格式不正确或字段名不支持时抛出
        """
        if ":" not in s:
            raise ValueError("sort 格式应为 'field:order',如 'rating:-1'")
        field, order_str = s.split(":", 1)
        if field not in Book.sort_fields():
            raise ValueError(f"不支持排序字段: {field}")
        order = int(order_str)
        if order not in {1, -1}:
            raise ValueError("排序方向只能是 1或 -1")
        return cls(field, order)
