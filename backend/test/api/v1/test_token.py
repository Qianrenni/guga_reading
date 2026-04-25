# import json
#
# from test.config import REFRESH_TOKEN
# from test.conftest import client
#
#
# def test_get_token(client):
#     url = '/token/get'
#     body = {
#         "username": "1093171693@qq.com",
#         "password": "123456",
#         'captcha': 't9va'
#     }
#     _header = {
#         'x-captcha-id': '4274a552-557c-43cb-af4c-1448b836af98',
#         'content-type': 'application/json'
#     }
#     response = client.post(url=url, data=json.dumps(body), headers=_header)
#     print(response.text)
#     assert response.status_code == 200
#
#
# def test_refresh_token(client):
#     _headers = {
#         'Authorization': f'Bearer {REFRESH_TOKEN}'
#     }
#     response = client.post(url='/token/refresh', headers=_headers)
#     print(response.text)
#     assert response.status_code == 200
