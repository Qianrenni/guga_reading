import os
from pathlib import Path

TOKEN = f"Bearer {os.getenv('TOKEN')}"
BASE_URL = "http://localhost:8000"
headers = {
    "Accept": "application/json",
    "Authorization": TOKEN,
    "Content-Type": "application/json",
}
headers_without_type = {"Accept": "application/json", "Authorization": TOKEN}
REFRESH_TOKEN = f"{os.getenv('REFRESH_TOKEN')}"
BASE_DIR = Path(__file__).resolve().parent
