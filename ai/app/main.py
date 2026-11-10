"""MenuPick AI 서버.

실행: ai/run_local.ps1 (기본 http://localhost:18090, 문서 /docs)
- 기본 엔진 graph: 키워드 검색 O(N)(N = 키워드 수×입력 길이) + 메뉴 그래프 BFS O(V+E), 밀리초 단위 응답(app/graph_engine.py).
- AI_ENGINE=llm: 로컬 무료 LLM(Ollama)을 먼저 쓰고, 꺼져 있거나 실패하면 graph로 대체(10~15초).
- AI_SERVICE_TOKEN을 설정하면 X-AI-Token 헤더가 같은 요청만 받습니다(Java 서버와 공유).
"""

import hmac
import logging
import os

from fastapi import Depends, FastAPI, Header, HTTPException

from . import graph_engine, llm_engine
from .schemas import (AssessRequest, AssessResponse, ElaborateRequest, ElaborateResponse, RecommendRequest,
                      RecommendResponse)

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s - %(message)s")

app = FastAPI(title="MenuPick AI", version="1.2.0")


def engine_name() -> str:
    return "llm" if os.environ.get("AI_ENGINE", "graph").strip().lower() == "llm" else "graph"


def require_token(x_ai_token: str | None = Header(default=None)) -> None:
    expected = os.environ.get("AI_SERVICE_TOKEN", "")
    if expected and not hmac.compare_digest(expected, x_ai_token or ""):
        raise HTTPException(status_code=401, detail={"error": "unauthorized", "message": "AI 토큰이 올바르지 않습니다."})


@app.get("/health")
def health() -> dict[str, object]:
    engine = engine_name()
    body: dict[str, object] = {
        "ok": True,
        "engine": engine,
        "graph": {"nodes": graph_engine.GRAPH.node_count, "edges": graph_engine.GRAPH.edge_count},
    }
    if engine == "llm":
        body["llm"] = llm_engine.enabled()
        body["model"] = llm_engine.MODEL
    return body


@app.post("/v1/recommend", response_model=RecommendResponse, dependencies=[Depends(require_token)])
def recommend(body: RecommendRequest) -> RecommendResponse:
    menu_name, reason = body.menuName.strip(), body.reason.strip()
    if engine_name() == "llm":
        result = llm_engine.recommend(menu_name, reason)
        if result is not None:
            return result
    return graph_engine.recommend(menu_name, reason)


@app.post("/v1/assess", response_model=AssessResponse, dependencies=[Depends(require_token)])
def assess(body: AssessRequest) -> AssessResponse:
    args = (body.menuName.strip(), body.reason.strip(), body.recommendedMenu.strip())
    if engine_name() == "llm":
        result = llm_engine.assess(*args)
        if result is not None:
            return result
    return graph_engine.assess(*args)


@app.post("/v1/elaborate", response_model=ElaborateResponse, dependencies=[Depends(require_token)])
def elaborate(body: ElaborateRequest) -> ElaborateResponse:
    """먹고 싶은 이유를 자세히: 사용자가 쓴 말은 그대로 두고 뒤에 AI 문장을 덧붙입니다."""
    menu_name, reason = body.menuName.strip(), body.reason.strip()
    if engine_name() == "llm" and graph_engine.assess(menu_name, reason, "").riskLevel != "high":
        added = llm_engine.elaborate(menu_name, reason)
        if added:
            base = reason if not reason or reason[-1] in ".!?~" else reason + "."
            full = f"{base} {added}".strip()[:2000]
            return ElaborateResponse(reason=full, added=added, engine="llm")
    full, added = graph_engine.elaborate(menu_name, reason)
    return ElaborateResponse(reason=full, added=added, engine="graph")
