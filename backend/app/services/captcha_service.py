from random import choices
from string import ascii_letters, digits
from uuid import uuid4

from captcha.image import ImageCaptcha

from app.core.config import SETTING
from app.services.cache_service import cache_delete, cache_get, cache_set


class CaptchaService:
    """验证码服务类,处理图形验证码和数字验证码的生成与验证"""

    @staticmethod
    def generate_captcha_text(length: int = 4) -> str:
        """
        生成随机验证码文本

        @param length: 验证码长度,默认4
        @return str: 随机验证码字符串,包含字母和数字
        """
        return "".join(choices(ascii_letters + digits, k=length))

    @staticmethod
    async def get_captcha(
        length: int = 4,
        width: int = 160,
        height: int = 60,
        expire: int = SETTING.CAPTCHA_EXPIRE,
    ) -> tuple[bytes, str]:
        """
        生成验证码图片并缓存

        @param length: 验证码文本长度,默认4
        @param width: 验证码图片宽度,默认160
        @param height: 验证码图片高度,默认60
        @param expire: 验证码有效期(秒),默认使用配置值
        @return tuple[bytes, str]: 返回验证码图片字节数据和验证码ID
        """
        text = CaptchaService.generate_captcha_text(length)
        image = ImageCaptcha(width=width, height=height)
        image_bytes = image.generate(text).getvalue()  # 直接获取 bytes

        captcha_id = str(uuid4())
        await cache_set(key_prefix=f"captcha:{captcha_id}", value=text, expire=expire)
        return image_bytes, captcha_id

    @staticmethod
    async def verify_captcha(captcha_id: str, captcha_text: str) -> bool:
        """
        验证验证码
        :param captcha_id: 验证码ID
        :param captcha_text: 验证码文本
        :return: 验证结果
        """
        cached_text = await cache_get(
            key_prefix=f"captcha:{captcha_id}",
        )
        if cached_text is None:
            return False
        if cached_text.lower() == captcha_text.lower():
            await cache_delete(
                key_prefix=f"captcha:{captcha_id}",
            )
            return True
        return False

    @staticmethod
    async def get_verify_code(
        key_prefix: str,
        length: int = 6,
        expire: int = SETTING.CAPTCHA_EXPIRE,
    ) -> str:
        """
        获取验证码
        :param length: 验证码长度
        :param expire: 验证码有效期
        :return: 验证码
        """
        if key_prefix is None or key_prefix == "":
            raise ValueError("key_prefix cannot be empty")
        answer = CaptchaService.generate_captcha_text(length)
        is_cached = await cache_set(key_prefix=key_prefix, value=answer, expire=expire)
        if not is_cached:
            raise ValueError("Previous verify code exists, please try again later")
        return answer

    @staticmethod
    async def verify_code(key_prefix: str, answer: str) -> bool:
        """
        验证验证码
        :param key_prefix: 验证码 key 前缀
        :param answer: 验证码答案
        :return: 验证结果
        """
        cached_answer = await cache_get(
            key_prefix=key_prefix,
        )
        if cached_answer is None:
            return False
        if answer == cached_answer:
            await cache_delete(
                key_prefix=key_prefix,
            )
            return True
        return False
