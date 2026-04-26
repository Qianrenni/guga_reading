# test/conftest.py
import pytest
from fastapi.testclient import TestClient
from test.config import headers


@pytest.fixture(scope="function")
def client():
    from app.main import app

    with TestClient(app) as c:
        yield c


def test_get_permission(client):
    response = client.get(url="/right/permission", headers=headers)
    assert response.status_code == 403
