from test.config import headers


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
