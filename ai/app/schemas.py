"""AI 서버 입출력 계약. 필드명은 Java 서버(menupick.ai.AiClient)와 맞춰야 합니다."""

from typing import Literal

from pydantic import BaseModel, Field

# graph = 키워드 검색 O(N), N=K×L + 메뉴 그래프 BFS(기본), llm = 로컬 무료 모델(Ollama, 선택)
Engine = Literal["graph", "llm"]
RiskLevel = Literal["low", "medium", "high"]
RiskFlag = Literal[
    "allergen",  # 주요 알레르기 유발 식품
    "food_safety",  # 날것·덜 익힘·보관 위험
    "not_food",  # 음식이 아니거나 먹으면 안 되는 것
    "inappropriate",  # 욕설·장난·부적절한 표현
    "prompt_injection",  # AI 지시를 바꾸려는 입력
    "unhealthy",  # 지나치게 자극적·고열량
]
RISK_FLAGS: tuple[str, ...] = RiskFlag.__args__  # type: ignore[attr-defined]


class RecommendRequest(BaseModel):
    menuName: str = Field(default="", max_length=255)
    reason: str = Field(default="", max_length=2000)


class RecommendResponse(BaseModel):
    recommendedMenu: str
    confidence: float = Field(ge=0.0, le=1.0)
    reasons: list[str]
    engine: Engine


class AssessRequest(BaseModel):
    menuName: str = Field(default="", max_length=255)
    reason: str = Field(default="", max_length=2000)
    recommendedMenu: str = Field(default="", max_length=255)


class AssessResponse(BaseModel):
    """AI는 판단 근거만 돌려줍니다. 삭제·공개·차단은 관리자가 결정합니다."""

    riskScore: float = Field(ge=0.0, le=1.0)
    riskLevel: RiskLevel
    flags: list[RiskFlag]
    reasons: list[str]
    engine: Engine


def risk_level(score: float) -> RiskLevel:
    if score >= 0.7:
        return "high"
    if score >= 0.4:
        return "medium"
    return "low"
