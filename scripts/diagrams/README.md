# 인프라 구성도 생성 스크립트

`docs/assets/infra-core.*`(핵심 흐름)와 `docs/assets/infra-detail.*`(상세 구성)를 만든다.
좌표·노드·선을 `core.py`·`detail.py`에 데이터로 적고, `render.py`가 SVG로 그린다. 표준 라이브러리만 쓴다.

```bash
cd scripts/diagrams
python3 core.py      # → docs/assets/infra-core.svg
python3 detail.py    # → docs/assets/infra-detail.svg

# PNG (2배 해상도). 창 크기는 각 스크립트의 W, H
CHROME="/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
"$CHROME" --headless --disable-gpu --hide-scrollbars --force-device-scale-factor=2 \
  --window-size=2220,1720 --screenshot=../../docs/assets/infra-core.png "file://$PWD/../../docs/assets/infra-core.svg"
"$CHROME" --headless --disable-gpu --hide-scrollbars --force-device-scale-factor=2 \
  --window-size=2520,1720 --screenshot=../../docs/assets/infra-detail.png "file://$PWD/../../docs/assets/infra-detail.svg"
```

- 두 그림은 같은 배치와 글자 배율(`FS = 1.7`)을 쓴다. 한쪽을 고치면 다른 쪽도 맞춘다.
- 인프라 결정이 바뀌면 그림보다 [README §3-8](../../docs/phase2/README.md#3-8-3-tier-수명과-destroy)과 결정 로그를 먼저 고친다.
- 아이콘: `icons/` — aws-icons@3.3.0(MIT), simple-icons@13(CC0, 파일명 `si-*`). 라이선스는 같은 폴더에 있다.
