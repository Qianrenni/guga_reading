import { get, post } from '../utils';
import { User } from '@guga-reading/types';
export const useApiAuth = {
  authMe: async (token_type: string, token: string) => {
    return await get<{ user: User }>(`/token/auth/me`, {
      headers: {
        Authorization: `${token_type} ${token}`,
      },
    });
  },
  login: async function (
    username: string,
    password: string,
    captcha: string,
    x_captcha_id: string,
  ) {
    return await post<{
      access_token: string;
      refresh_token: string;
      token_type: string;
      user: User;
    }>(
      `/token/get`,
      {
        username: username,
        password: password,
        captcha: captcha,
      },
      {
        headers: {
          'X-Captcha-Id': x_captcha_id,
        },
      },
    );
  },
  refreshToken: async function (token_type: string, refresh_token: string) {
    return await post<{
      access_token: string;
      refresh_token: string;
      token_type: string;
      user: User;
    }>(
      `/token/refresh`,
      {},
      {
        headers: {
          Authorization: `${token_type} ${refresh_token}`,
        },
      },
    );
  },
  verifyEmail: async (email: string) => {
    return await post<null>('/token/verify_email', {
      email: email,
    });
  },
  register: async (
    username: string,
    password: string,
    email: string,
    captcha: string,
    x_captcha_id: string,
    avatar: string = '',
  ) => {
    return await post<null>(
      `/user/register`,
      {
        user: {
          username: username,
          password: password,
          email: email,
          avatar: avatar,
        },
        captcha: captcha,
      },
      {
        headers: {
          'X-Captcha-Id': x_captcha_id,
        },
      },
    );
  },
};
