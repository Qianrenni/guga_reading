# test/conftest.py
import pytest
from fastapi.testclient import TestClient
from test.config import headers


@pytest.fixture(scope="function")
def client():
    from app.main import app

    with TestClient(app) as c:
        yield c


def test_get_user_shelf(client):
    """测试获取用户书架"""
    response = client.get("/shelf/get", headers=headers)
    assert response.status_code == 200


def test_delete_book_from_shelf(client):
    """测试删除书架书籍"""
    response = client.delete("/shelf/delete/1", headers=headers)
    assert response.status_code == 200


def test_add_book_to_shelf(client):
    """测试添加书籍到书架"""
    data = {"book_id": 1}
    response = client.post("/shelf/add", headers=headers, json=data)
    assert response.status_code == 201
