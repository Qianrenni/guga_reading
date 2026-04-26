import json

import pytest
from fastapi.testclient import TestClient
from test.config import headers


@pytest.fixture(scope="function")
def client():
    from app.main import app

    with TestClient(app) as c:
        yield c


def test_get_book_chapter_draft(client):
    response = client.get("/admin/book-chapter-draft", headers=headers)
    assert response.status_code == 200
    print(json.dumps(response.json(), indent=4, ensure_ascii=False))
