def test_forgot_password(client):
    """测试忘记密码"""
    email = "1093171693@qq.com"
    response = client.get(f"/user/forgot-password?user_account={email}")
    assert response.status_code == 204


# def test_patch_forgot_password(client):
#     """测试忘记密码修改密码"""
#     email = '1093171693@qq.com'
#     verify_code = '0fH3Yi'
#     password = '123456'
#     response = client.patch(
#         '/user/forgot-password',
#         json={
#             'user_account': email,
#             'verify_code': verify_code,
#             'password': password
#         }
#     )
#     assert response.status_code == 204
