from pathlib import Path

import pytest
from fastapi.testclient import TestClient
from test.config import headers, headers_without_type


@pytest.fixture(scope="function")
def client():
    from app.main import app

    with TestClient(app) as c:
        yield c


def test_get_own_books(client):
    response = client.get("/author/book", headers=headers)
    assert response.status_code == 200


def get_author_book_chapter(client, book_id):
    response = client.get(f"/author/chapter?book_id={book_id}", headers=headers)
    assert response.status_code == 200
    data = response.json()["data"]
    return data


def test_update_author_book_chapter(client):
    import uuid

    content = f"{uuid.uuid4()}"
    data = {
        "book_id": 1,
        "title": "test",
        "content": content,
        "chapter_id": 1,
    }
    response = client.patch("/author/chapter", json=data, headers=headers)
    assert response.status_code == 204
    result = get_author_book_chapter(client, 1)
    target_id = -1
    for book in result:
        if book["title"] == data["title"]:
            target_id = book["id"]
    assert target_id != -1
    response = client.request(
        "DELETE",
        f"/author/chapter?book_id=1&chapter_id={target_id}",
        headers=headers,
    )
    assert response.status_code == 204


def test_create_book(client):
    import uuid

    from test.config import BASE_DIR

    name = f"{uuid.uuid4()}"
    # 准备普通表单字段
    form_data = {
        "name": name,
        "author": "test",
        "description": "test",
        "category": "test",
        "tags": "test1 test2",
    }

    # 打开文件用于上传(注意:以二进制模式打开,并保持打开状态)
    with Path.open(BASE_DIR / "data" / "cover.webp", "rb") as f:
        files = {
            "cover": ("test.webp", f, "image/webp")
        }  # (filename, file_obj, content_type)
        response = client.post(
            "/author/book",
            data=form_data,  # 普通字段
            files=files,  # 文件字段
            headers=headers_without_type,  # 通常 multipart 请求不需要额外 headers,除非有 auth
        )
    assert response.status_code == 201

    response = client.get("/author/book", headers=headers)
    assert response.status_code == 200
    result = response.json()
    delete_item = None
    for item in result["data"]:
        if item["name"] == name:
            delete_item = item
            break
    assert delete_item is not None
    if delete_item:
        response = client.request(
            "DELETE",
            f"/author/book?id={delete_item['id']}",
            headers=headers,
        )
        assert response.status_code == 204


def test_update_book(client):
    import uuid

    name = f"{uuid.uuid4()}"
    data = {
        "id": 1,
        "name": name,
        "author": "test",
        "description": "test",
        "category": "test",
        "tags": "test1 test2",
    }
    response = client.patch("/author/book", data=data, headers=headers_without_type)
    assert response.status_code == 204
    response = client.get("/author/book", headers=headers)
    assert response.status_code == 200
    result = response.json()
    delete_item = None
    print(result)
    for item in result["data"]:
        if item["name"] == name:
            delete_item = item
            break
    assert delete_item is not None
    if delete_item:
        response = client.request(
            "DELETE",
            f"/author/book?id={delete_item['id']}",
            headers=headers,
        )
        assert response.status_code == 204
