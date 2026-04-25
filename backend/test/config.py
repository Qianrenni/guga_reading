TOKEN = "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ7XCJpZFwiOiAxLCBcInVzZXJuYW1lXCI6IFwicWlhbnJlbm5pXCIsIFwiZW1haWxcIjogXCIxMDkzMTcxNjkzQHFxLmNvbVwiLCBcImF2YXRhclwiOiBcIlwiLCBcImlzX2FjdGl2ZVwiOiB0cnVlLCBcInJpZ2h0XCI6IFszODIzXX0iLCJleHAiOjE3NjgxOTkzMzEuNjg3NjMxMX0.sT5X7vjKeGBLk44x72jn1CXsxqX7YotkTDIDGCRfh8I"
BASE_URL = "http://localhost:8000"
headers = {
    "Accept": "application/json",
    "Authorization": TOKEN,
    "Content-Type": "application/json",
}
headers_without_type = {"Accept": "application/json", "Authorization": TOKEN}
REFRESH_TOKEN = "jI0FQ-73NmbMVoXttiPa-agtIujw2eb2_eQDF2aQiJi5XRl1Rf8MYcMNZwMLFTXysSu5es9Sbq9wnFa9hRNA2A"
from pathlib import Path

BASE_DIR = Path(__file__).resolve().parent
