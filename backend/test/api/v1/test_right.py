from test.config import headers


def test_get_permission(client):
    response = client.get(url="/right/permission", headers=headers)
    assert response.status_code == 200
