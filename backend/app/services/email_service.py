# qq_email_sender.py
import smtplib
from email.header import Header
from email.mime.multipart import MIMEMultipart
from email.mime.text import MIMEText

from app.middleware.logging import logger


class QQEmailSender:
    def __init__(
        self,
        sender_email: str,
        authorization_code: str,
        smtp_server: str = "smtp.qq.com",
        smtp_port: int = 465,
    ):
        """
        初始化 QQ 邮箱发送器

        :param sender_email: 发件人邮箱(如 xxx@qq.com)
        :param authorization_code: QQ 邮箱 SMTP 授权码(16位)
        :param smtp_server: SMTP 服务器地址,默认 smtp.qq.com
        :param smtp_port: SMTP 端口,默认 587(STARTTLS)
        """
        self.sender_email = sender_email
        self.authorization_code = authorization_code
        self.smtp_server = smtp_server
        self.smtp_port = smtp_port

    def send_email(
        self,
        to_emails: list[str],
        subject: str,
        body: str,
        is_html: bool = False,
        cc_emails: list[str] | None = None,
    ) -> bool:
        """
        发送邮件

        :param to_emails: 收件人列表,如 ["user1@example.com", "user2@example.com"]
        :param subject: 邮件主题
        :param body: 邮件正文
        :param is_html: 是否为 HTML 格式,默认 False(纯文本)
        :param cc_emails: 抄送列表(可选)
        :return: 成功返回 True,失败返回 False
        """
        try:
            # 创建邮件对象
            msg = MIMEMultipart()
            msg["From"] = Header(self.sender_email)
            msg["To"] = Header(", ".join(to_emails))
            if cc_emails:
                msg["Cc"] = Header(", ".join(cc_emails))
            msg["Subject"] = Header(subject)

            # 添加正文
            mime_type = "html" if is_html else "plain"
            msg.attach(MIMEText(body, mime_type, "utf-8"))

            # 所有收件人(包括抄送)
            all_recipients = to_emails + (cc_emails or [])

            # 连接 SMTP 服务器
            with smtplib.SMTP_SSL(self.smtp_server, self.smtp_port) as server:
                server.login(self.sender_email, self.authorization_code)
                server.sendmail(self.sender_email, all_recipients, msg.as_string())

            logger.info(f"邮件已成功发送至: {', '.join(to_emails)}")
            return True

        except smtplib.SMTPAuthenticationError:
            logger.error("SMTP 认证失败:请检查邮箱或授权码是否正确")
        except smtplib.SMTPRecipientsRefused:
            logger.error("收件人地址被拒绝:请检查邮箱格式或是否存在")
        except smtplib.SMTPServerDisconnected:
            logger.error("SMTP 服务器断开连接:可能是网络或配置问题")
        except OSError as e:
            # 特别处理 QQ 邮箱的假失败(虽然用了 SSL,但极少数情况仍可能出现)
            if e.args == (-1, b"\x00\x00\x00"):
                logger.warning("检测到 QQ 邮箱空响应,视为发送成功")
                return True
            logger.error(f"网络错误: {e}")
        except Exception as e:
            logger.error(f" 邮件发送失败:  {e!s}")
        return False


email_sender = QQEmailSender(
    sender_email="2112183503@qq.com",
    authorization_code="gbovhuasbrtzedji",  # ← 替换为你自己的授权码
)
# ========================
# 使用示例
# ========================
if __name__ == "__main__":
    # 方式1:直接传入授权码(开发测试用)
    sender = QQEmailSender(
        sender_email="2112183503@qq.com",
        authorization_code="gbovhuasbrtzedji",  # ← 替换为你自己的授权码
    )
    # 发送纯文本邮件
    success = sender.send_email(
        to_emails=["3319913902@qq.com"],
        subject="欢迎投递!",
        body="感谢您的注册,祝您使用愉快!",
    )
