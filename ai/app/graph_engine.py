"""그래프 기반 추천·위험 판별 엔진 (기본 엔진).

LLM(10~15초) 대신 바로 답합니다.

키워드 찾기 — 키워드마다 입력에서 부분 문자열 검색 (원래 규칙 엔진 방식)
  - K = 키워드 수, L = 입력 길이일 때 K×L 번 비교 → N = K×L 로 두면 O(N).
  - Python의 `in`은 C로 구현돼 있어, 이 크기(K≈50~95, L≤2000)에서는
    Aho-Corasick(O(L + 일치 수))을 파이썬으로 짠 것보다 2~6배 빠릅니다(2026-11-10 측정).

추천 — 메뉴 지식 그래프 + 다중 시작점 BFS
  - 노드: 메뉴(예: 김치찌개)와 특징(예: 매운, 국물, 따뜻한) / 간선: 메뉴–특징, 비슷한 메뉴–메뉴
  - 입력에서 찾은 특징·메뉴를 시작점으로 BFS를 한 번 돌립니다: O(V+E) (V = 노드 수, E = 간선 수)
  - 점수 = 시작점과 바로 이웃한 수(일치한 특징 수), 같으면 BFS 거리가 가까운 메뉴, 그다음 목록 순서.
  - 요청당 O(N + V + E).

위험 판별 — 위험 키워드 검색 O(N), N = K×L.
"""

from __future__ import annotations

from collections import deque
from typing import Iterable

from .schemas import AssessResponse, RecommendResponse, risk_level


def find_keywords(text: str, keywords: Iterable[str]) -> set[str]:
    """입력에 들어 있는 키워드를 모두 찾습니다. O(N), N = K×L (K = 키워드 수, L = 입력 길이)."""
    lowered = text.lower()
    return {kw for kw in keywords if kw in lowered}


# ---------------------------------------------------------------- 메뉴 그래프

# 특징 → 입력에서 찾을 표현. 한 글자 표현(국, 면, 밥, 탕)은 다른 낱말("먹으면", "설탕")과 겹쳐 쓰지 않습니다.
ATTRIBUTES: dict[str, tuple[str, ...]] = {
    "매운": ("매운", "맵게", "매콤", "얼큰", "칼칼", "spicy"),
    "따뜻한": ("따뜻", "뜨끈", "추운", "추워", "쌀쌀", "비 오는", "비오는", "비가 와", "비가 오", "비 와", "겨울"),
    "시원한": ("시원", "더운", "더워", "여름", "차가운"),
    "국물": ("국물", "찌개", "전골"),
    "채소": ("채소", "야채", "샐러드", "나물", "건강"),
    "비빔": ("비빔", "섞어", "섞으면", "섞은"),
    "면": ("국수", "면요리", "면 요리", "면이", "면을", "누들", "noodle"),
    "고기": ("고기", "육류", "돼지", "소고기", "닭"),
    "해산물": ("해산물", "생선", "해물", "오징어"),
    "든든한": ("든든", "배고", "배가 고", "푸짐", "많이"),
    "가벼운": ("가볍", "가벼운", "담백", "다이어트", "깔끔"),
    "달콤한": ("달콤", "단 거", "단것", "달달"),
    "밥": ("밥이", "밥을", "밥 종류", "덮밥", "쌀"),
    "간편": ("간단", "간편", "빨리", "바쁜"),
    "튀김": ("튀김", "바삭", "튀긴"),
}

# 메뉴 → 특징. 순서가 같은 점수일 때의 우선순위입니다. 모두 급식으로 낼 수 있는 메뉴만 둡니다.
MENUS: dict[str, tuple[str, ...]] = {
    "떡볶이": ("매운", "간편"),
    "김치찌개": ("국물", "따뜻한", "매운", "밥"),
    "비빔밥": ("채소", "비빔", "밥", "가벼운"),
    "된장찌개": ("국물", "따뜻한", "채소", "밥"),
    "잔치국수": ("면", "국물", "따뜻한", "가벼운"),
    "칼국수": ("면", "국물", "따뜻한", "해산물"),
    "비빔국수": ("면", "비빔", "매운", "시원한"),
    "냉면": ("면", "시원한", "국물"),
    "라면": ("면", "국물", "매운", "간편"),
    "제육볶음": ("고기", "매운", "밥", "든든한"),
    "불고기": ("고기", "달콤한", "밥", "든든한"),
    "닭갈비": ("고기", "매운", "든든한"),
    "돈까스": ("고기", "튀김", "든든한"),
    "탕수육": ("고기", "튀김", "달콤한"),
    "삼계탕": ("고기", "국물", "따뜻한", "든든한"),
    "떡국": ("국물", "따뜻한", "든든한"),
    "미역국": ("국물", "따뜻한", "해산물", "가벼운"),
    "오징어볶음": ("해산물", "매운", "밥"),
    "생선구이": ("해산물", "가벼운", "밥"),
    "카레라이스": ("밥", "든든한", "간편", "달콤한"),
    "볶음밥": ("밥", "간편"),
    "김밥": ("밥", "간편", "채소"),
    "샐러드": ("채소", "가벼운", "시원한"),
}

# 비슷한 메뉴끼리 잇는 간선(BFS로 한 단계 떨어진 메뉴를 근거로 보여줄 때 씀).
SIMILAR: tuple[tuple[str, str], ...] = (
    ("김치찌개", "된장찌개"), ("비빔밥", "비빔국수"), ("잔치국수", "칼국수"), ("냉면", "비빔국수"),
    ("제육볶음", "오징어볶음"), ("돈까스", "탕수육"), ("볶음밥", "김밥"), ("떡국", "미역국"),
)

DEFAULT_MENU = "비빔밥"


class MenuGraph:
    """인접 리스트 그래프. 노드 id: 'a:특징' / 'm:메뉴'."""

    def __init__(self) -> None:
        self.adj: dict[str, list[str]] = {}
        for attr in ATTRIBUTES:
            self.adj[f"a:{attr}"] = []
        for menu, attrs in MENUS.items():
            self.adj[f"m:{menu}"] = []
            for attr in attrs:
                self._link(f"m:{menu}", f"a:{attr}")
        for a, b in SIMILAR:
            self._link(f"m:{a}", f"m:{b}")
        self.order = {menu: i for i, menu in enumerate(MENUS)}

        # 입력 표현 → 시작 노드. 메뉴 이름도 직접 찾습니다(예: "신라면 먹고 싶다" → 라면).
        self._phrase_to_node: dict[str, str] = {}
        for attr, phrases in ATTRIBUTES.items():
            for phrase in phrases:
                self._phrase_to_node[phrase.lower()] = f"a:{attr}"
        for menu in MENUS:
            self._phrase_to_node[menu.lower()] = f"m:{menu}"

    def _link(self, a: str, b: str) -> None:
        self.adj[a].append(b)
        self.adj[b].append(a)

    @property
    def node_count(self) -> int:
        return len(self.adj)

    @property
    def edge_count(self) -> int:
        return sum(len(v) for v in self.adj.values()) // 2

    def seeds(self, text: str) -> set[str]:
        return {self._phrase_to_node[p] for p in find_keywords(text, self._phrase_to_node)}

    def bfs(self, seeds: set[str]) -> dict[str, int]:
        """다중 시작점 BFS. 모든 노드·간선을 최대 한 번씩만 봅니다: O(V+E)."""
        dist = {s: 0 for s in seeds}
        queue = deque(seeds)
        while queue:
            node = queue.popleft()
            for nxt in self.adj[node]:
                if nxt not in dist:
                    dist[nxt] = dist[node] + 1
                    queue.append(nxt)
        return dist

    def recommend(self, text: str) -> tuple[str, list[str], float]:
        seeds = self.seeds(text)
        if not seeds:
            return DEFAULT_MENU, ["뚜렷한 단서가 없어 기본 메뉴를 추천합니다."], 0.2

        dist = self.bfs(seeds)
        # 일치 점수: 시작점의 이웃만 훑습니다(시작점 차수 합 ≤ 2E).
        hits: dict[str, int] = {}
        matched_attrs: dict[str, list[str]] = {}
        for seed in seeds:
            if seed.startswith("m:"):
                hits[seed] = hits.get(seed, 0) + 2  # 메뉴 이름을 직접 말했으면 가산
                continue
            for nxt in self.adj[seed]:
                if nxt.startswith("m:"):
                    hits[nxt] = hits.get(nxt, 0) + 1
                    matched_attrs.setdefault(nxt, []).append(seed[2:])

        candidates = [n for n in self.adj if n.startswith("m:") and n in dist]
        best = min(candidates, key=lambda n: (-hits.get(n, 0), dist[n], self.order[n[2:]]))
        menu = best[2:]

        attrs = sorted(matched_attrs.get(best, []), key=list(ATTRIBUTES).index)
        reasons = []
        if f"m:{menu}" in seeds:
            reasons.append(f"입력에 '{menu}'이(가) 있습니다.")
        if attrs:
            reasons.append(f"원하는 특징({', '.join(attrs)})과 가장 많이 연결된 메뉴입니다.")
        similar = [n[2:] for n in self.adj[best] if n.startswith("m:")]
        if similar:
            reasons.append(f"비슷한 메뉴: {', '.join(similar)}")
        confidence = min(0.9, 0.35 + 0.15 * hits.get(best, 0))
        return menu, reasons[:3], round(confidence, 2)


# ---------------------------------------------------------------- 위험 판별

# (플래그, 점수, 키워드, 근거)
RISK_RULES: tuple[tuple[str, float, tuple[str, ...], str], ...] = (
    ("not_food", 0.9, ("세제", "독극물", "독버섯", "농약", "플라스틱", "벌레", "담배", "시가", "소주", "맥주",
                       "양주", "막걸리", "와인", "위스키", "보드카", "사케", "고량주", "칵테일", "하이볼",
                       "샴페인", "소맥", "약물"),
     "음식이 아니거나 급식으로 제공할 수 없는 항목입니다."),
    ("prompt_injection", 0.8, ("ignore previous", "system prompt", "이전 지시", "지시를 무시", "프롬프트"),
     "AI 지시를 바꾸려는 문장이 포함돼 있습니다."),
    ("inappropriate", 0.7, ("시발", "씨발", "병신", "개새", "존나", "ㅅㅂ"),
     "부적절한 표현이 포함돼 있습니다."),
    ("food_safety", 0.6, ("날것", "생고기", "육회", "생선회", "회덮밥", "덜 익", "생굴", "곰팡"),
     "날것이나 덜 익힌 음식은 식중독 위험이 있습니다."),
    ("allergen", 0.4, ("땅콩", "견과", "새우", "꽃게", "대게", "게장", "갑각류", "메밀", "복숭아", "우유",
                       "달걀", "계란"),
     "주요 알레르기 유발 식품이 포함될 수 있습니다."),
    ("unhealthy", 0.3, ("핵불닭", "초매운", "엽기", "매운맛 최고", "에너지 드링크", "탄산"),
     "지나치게 자극적이거나 영양 균형이 맞지 않을 수 있습니다."),
)

_KEYWORD_TO_RULE = {kw.lower(): i for i, rule in enumerate(RISK_RULES) for kw in rule[2]}

GRAPH = MenuGraph()


def recommend(menu_name: str, reason: str) -> RecommendResponse:
    menu, reasons, confidence = GRAPH.recommend(f"{menu_name} {reason}")
    return RecommendResponse(recommendedMenu=menu, confidence=confidence, reasons=reasons, engine="graph")


# '술'은 "기술·미술·수술"과 겹치므로 부분 일치 대신 낱말이 '술'로 시작할 때만 봅니다(술, 술안주, 술집). O(L).
_ALCOHOL_PREFIX = "술"
_NOT_FOOD_RULE = 0


def _mentions_alcohol(text: str) -> bool:
    return any(token.startswith(_ALCOHOL_PREFIX) for token in text.split())


def assess(menu_name: str, reason: str, recommended_menu: str) -> AssessResponse:
    text = f"{menu_name} {recommended_menu} {reason}"
    found = find_keywords(text, _KEYWORD_TO_RULE)
    hit_rule_set = {_KEYWORD_TO_RULE[kw] for kw in found}
    if _mentions_alcohol(text):
        hit_rule_set.add(_NOT_FOOD_RULE)
    hit_rules = sorted(hit_rule_set)  # 규칙 표 순서 유지
    flags = [RISK_RULES[i][0] for i in hit_rules]
    reasons = [RISK_RULES[i][3] for i in hit_rules] or ["위험 키워드를 찾지 못했습니다."]
    score = max((RISK_RULES[i][1] for i in hit_rules), default=0.05)
    return AssessResponse(riskScore=score, riskLevel=risk_level(score), flags=flags,  # type: ignore[arg-type]
                          reasons=reasons, engine="graph")


# ---------------------------------------------------------------- 이유 자세히 쓰기

# 특징 → 덧붙일 문장. 사용자가 쓴 말 뒤에 붙입니다.
ATTRIBUTE_SENTENCES: dict[str, str] = {
    "매운": "매콤한 맛으로 입맛을 확 살리고 싶어요.",
    "따뜻한": "따뜻한 음식으로 몸을 녹이고 싶어요.",
    "시원한": "시원하게 더위를 식히고 싶어요.",
    "국물": "국물이 있으면 밥이랑 같이 든든하게 먹을 수 있어요.",
    "채소": "채소가 들어가서 건강하게 먹을 수 있으면 좋겠어요.",
    "비빔": "여러 재료를 비벼 먹으면 맛이 잘 어우러질 것 같아요.",
    "면": "후루룩 먹는 면 요리가 당겨요.",
    "고기": "고기가 들어가면 힘이 나서 오후 수업도 버틸 수 있을 것 같아요.",
    "해산물": "해산물의 감칠맛이 생각나요.",
    "든든한": "배부르게 먹고 남은 하루도 힘내고 싶어요.",
    "가벼운": "부담 없이 가볍게 먹고 싶어요.",
    "달콤한": "달콤한 맛으로 기분 전환을 하고 싶어요.",
    "밥": "밥과 함께 먹으면 든든할 것 같아요.",
    "간편": "간편하게 먹을 수 있어서 점심시간에 딱 좋아요.",
    "튀김": "바삭한 식감이 그리워요.",
}
MAX_ADDED_SENTENCES = 3
GENERIC_SENTENCES = [
    "요즘 급식에서 먹고 싶었던 메뉴가 나오면 점심시간이 더 기다려질 것 같아요.",
    "친구들과 다 같이 맛있게 먹을 수 있는 메뉴면 좋겠어요.",
]


def _has_final_consonant(word: str) -> bool:
    last = word[-1] if word else ""
    return "가" <= last <= "힣" and (ord(last) - 0xAC00) % 28 != 0


def _josa(word: str, with_final: str, without_final: str) -> str:
    return word + (with_final if _has_final_consonant(word) else without_final)


def elaborate(menu_name: str, reason: str) -> tuple[str, str]:
    """(전체 이유, 덧붙인 부분). 사용자가 쓴 말은 바꾸지 않고 뒤에 문장을 덧붙입니다.

    입력에서 찾은 특징 + 메뉴(그래프에 있으면 그 메뉴의 특징)로 문장을 고릅니다. O(N + V + E).
    """
    original = reason.strip()
    menu = menu_name.strip()
    # 위험한 메뉴(술·담배 등)나 그래프에 없는 말은 문장에 넣지 않습니다.
    if menu and assess(menu, "", "").riskLevel == "high":
        menu = ""

    seeds = GRAPH.seeds(f"{menu} {original}")
    attrs = [s[2:] for s in seeds if s.startswith("a:")]
    target = menu if f"m:{menu}" in GRAPH.adj else ""
    if not target:
        target = GRAPH.recommend(f"{menu} {original}")[0] if seeds else ""
    if target:
        attrs += [a for a in MENUS[target] if a not in attrs]
    attrs.sort(key=list(ATTRIBUTES).index)  # 같은 입력이면 항상 같은 문장 순서

    sentences: list[str] = []
    if not original:
        sentences.append(f"{_josa(target or '오늘 메뉴', '이', '가')} 먹고 싶어요.")
    for attr in attrs:
        sentence = ATTRIBUTE_SENTENCES[attr]
        if sentence not in sentences:
            sentences.append(sentence)
        if len(sentences) >= MAX_ADDED_SENTENCES:
            break
    if target and len(sentences) < MAX_ADDED_SENTENCES + 1:
        sentences.append(f"{_josa(target, '은', '는')} 급식으로 나오면 다 같이 맛있게 먹을 수 있을 것 같아요.")
    if not attrs and not target:
        # 단서가 하나도 없을 때(예: "아무거나", "그냥요")도 이유를 조금 더 채워 줍니다.
        sentences += GENERIC_SENTENCES

    added = " ".join(sentences)
    if original and original[-1] not in ".!?~":
        original += "."
    full = f"{original} {added}".strip()
    return full[:2000], added
