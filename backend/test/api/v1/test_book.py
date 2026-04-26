# test/conftest.py
import pytest
from fastapi.testclient import TestClient
from test.config import headers


@pytest.fixture(scope="function")
def client():
    from app.main import app

    with TestClient(app) as c:
        yield c


def test_get_chapter_content(client):
    """测试获取章节内容"""
    response = client.get(url="/book/chapter/2?book_id=1", headers=headers)
    print(response.json())
    assert response.status_code == 200


def test_get_chapter_content_with_params(client):
    """测试通过参数获取章节内容"""
    book_id = 1
    chapter_index = 1
    response = client.get(f"/book/chapter/{book_id}/{chapter_index}", headers=headers)
    assert response.status_code == 200


def test_get_book_info(client):
    """测试获取图书信息"""
    book_id = 1
    response = client.get(f"/book/{book_id}", headers=headers)
    print(response.json())
    assert response.status_code == 200


def test_get_book_list(client):
    """测试获取图书列表"""
    query = "".join([f"book_ids={book_id}&" for book_id in [1, 2, 3]])
    response = client.get(f"/book/list?{query}", headers=headers)
    assert response.status_code == 200


def test_get_book_total(client):
    """测试获取图书总数"""
    response = client.get("/book/total", headers=headers)
    assert response.status_code == 200
