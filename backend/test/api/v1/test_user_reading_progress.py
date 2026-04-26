# test/conftest.py
import pytest
from fastapi.testclient import TestClient
from test.config import headers


@pytest.fixture(scope="function")
def client():
    from app.main import app

    with TestClient(app) as c:
        yield c


def test_delete_user_reading_progress(client):
    """测试删除用户阅读进度"""
    book_id = 1
    response = client.delete(
        f"/user_reading_progress/delete/{book_id}", headers=headers
    )
    assert response.status_code == 200


def test_add_user_reading_progress(client):
    """测试添加用户阅读进度"""
    data = {"book_id": 1, "last_chapter_id": 2, "last_position": 1}
    response = client.patch("/user_reading_progress/add", headers=headers, json=data)
    assert response.status_code == 200


def test_user_reading_progress(client):
    """测试获取用户阅读进度"""
    response = client.get("/user_reading_progress/get", headers=headers)
    assert response.status_code == 200
