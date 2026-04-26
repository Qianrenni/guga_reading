from pathlib import Path

TOKEN = "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ7XCJpZFwiOiAxLCBcInVzZXJuYW1lXCI6IFwiUWlhbnJlbm5pXCIsIFwiZW1haWxcIjogXCIxMDkzMTcxNjkzQHFxLmNvbVwiLCBcImF2YXRhclwiOiBcIlwiLCBcImlzX2FjdGl2ZVwiOiB0cnVlLCBcInJpZ2h0XCI6IFsyMjNdfSIsImV4cCI6MTc3NzI2NzQ2OC40NzkxMzk4fQ.q_XW7EPzxaOdqVGqooUk6j5CVwf5xnE5KShe1jY5QTY"
BASE_URL = "http://localhost:8000"
headers = {
    "Accept": "application/json",
    "Authorization": TOKEN,
    "Content-Type": "application/json",
}
headers_without_type = {"Accept": "application/json", "Authorization": TOKEN}
REFRESH_TOKEN = "E5MeCIZyPeqb_EFYpuotMeZI5B1kSxu7aWKNnZDymyaG7S4scBmuROiOnxjCktreC6AffoJ4GR32HXyG8sCg3g"
BASE_DIR = Path(__file__).resolve().parent
