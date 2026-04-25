# test/conftest.py

import pytest
from fastapi.testclient import TestClient


@pytest.fixture(scope="function")
def client():
    from app.main import app

    with TestClient(app) as c:
        yield c
