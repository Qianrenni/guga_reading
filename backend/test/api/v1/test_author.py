from test.config import headers, headers_without_type
from test.conftest import client


def test_get_own_books(client: client):
    response = client.get("/author/book", headers=headers)
    assert response.status_code == 200


def test_create_book(client):
    from test.config import BASE_DIR

    # 准备普通表单字段
    form_data = {
        "name": "test",
        "author": "test",
        "description": "test",
        "category": "test",
        "tags": "test1 test2",
    }

    # 打开文件用于上传(注意:以二进制模式打开,并保持打开状态)
    with open(BASE_DIR / "data" / "cover.webp", "rb") as f:
        files = {
            "cover": ("test.webp", f, "image/webp")
        }  # (filename, file_obj, content_type)
        response = client.post(
            "/author/create/book",
            data=form_data,  # 普通字段
            files=files,  # 文件字段
            headers=headers_without_type,  # 通常 multipart 请求不需要额外 headers,除非有 auth
        )
    assert response.status_code == 201


def test_create_book_chapter(client):
    data = {"book_id": 1, "title": "test", "content": "test", "chapter_index": 1}
    response = client.post("/author/create/chapter", json=data, headers=headers)
    assert response.status_code == 201


def test_update_book_chapter(client):
    data = {"book_id": 1, "title": "test", "content": ["test"], "chapter_index": 1}
    response = client.patch("/author/update/chapter", json=data, headers=headers)
    print(response.json())
    assert response.status_code == 201
