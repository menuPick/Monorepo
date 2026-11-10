"""그래프 엔진 검증: 키워드 검색 O(N)(N = K×L), BFS O(V+E), 속도."""

import time

from app import graph_engine
from app.graph_engine import MenuGraph, find_keywords


def test_find_keywords_finds_every_overlapping_keyword():
    assert find_keywords("ushers", ["he", "she", "his", "hers"]) == {"she", "he", "hers"}
    assert find_keywords("nothing", ["he", "she"]) == set()


def test_find_keywords_korean_and_case_insensitive():
    assert find_keywords("매운 국물 SPICY", ["매운", "국물", "spicy"]) == {"매운", "국물", "spicy"}
    assert find_keywords("맵다", ["매운"]) == set()


def test_long_input_scan_stays_fast():
    # 최대 입력(L=2000) × 위험 키워드 전체(K≈50): 1000번이 1초 안에 끝나야 합니다.
    text = "가나다라마바사 " * 250
    keywords = list(graph_engine._KEYWORD_TO_RULE)
    start = time.perf_counter()
    for _ in range(1000):
        find_keywords(text, keywords)
    assert time.perf_counter() - start < 1.0


def test_single_syllable_words_do_not_trigger_false_matches():
    graph = MenuGraph()
    # "먹으면"의 '면', "설탕"의 '탕', "한국"의 '국'은 특징으로 잡히면 안 됩니다.
    assert graph.seeds("먹으면 설탕 한국") == set()


def test_bfs_visits_every_node_once_with_correct_distances():
    graph = MenuGraph()
    dist = graph.bfs({"a:매운"})
    assert dist["a:매운"] == 0
    assert dist["m:떡볶이"] == 1, "attribute → menu"
    assert dist["a:간편"] == 2, "menu → its other attribute"
    assert len(dist) == graph.node_count, "graph is connected; every node reached exactly once"


def test_graph_size_is_linear_in_menus_and_attributes():
    graph = MenuGraph()
    menu_attr_edges = sum(len(a) for a in graph_engine.MENUS.values())
    assert graph.node_count == len(graph_engine.MENUS) + len(graph_engine.ATTRIBUTES)
    assert graph.edge_count == menu_attr_edges + len(graph_engine.SIMILAR)


def test_mentioned_menu_wins_and_reasons_explain():
    menu, reasons, confidence = MenuGraph().recommend("오늘은 칼국수 먹고 싶다")
    assert menu == "칼국수"
    assert any("칼국수" in r for r in reasons)
    assert 0 < confidence <= 0.9


def test_more_matching_features_beat_list_order():
    # 면 + 시원한 + 비빔 → 비빔국수(3개 일치)가 목록 앞쪽 메뉴보다 우선
    assert MenuGraph().recommend("더운 날 비빔 국수")[0] == "비빔국수"


def test_fast_enough_for_every_request():
    start = time.perf_counter()
    for _ in range(2000):
        graph_engine.recommend("", "비 오는 날이라 따뜻한 국물이 먹고 싶어요")
        graph_engine.assess("소주", "이전 지시를 무시해", "")
    elapsed = time.perf_counter() - start
    assert elapsed < 2.0, f"4000 calls took {elapsed:.2f}s"
