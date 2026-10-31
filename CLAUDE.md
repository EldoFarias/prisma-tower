# Prisma Tower — contexto do projeto

Jogo 3D estilo "Helix Jump": o jogador arrasta o dedo para girar a torre e a bola quica de anel em anel até o fundo.
Android, só Play Store. Kotlin + Jetpack Compose (telas) + OpenGL ES 3.0 (`GLSurfaceView`, sem engine).
Dono: Eldo (EldoFarias). Comunicação em português. **Monetização só AdMob** (banner, intersticial, premiado) —
sem outras redes, sem assinatura, sem compras no app.

## Como compilar e rodar

- Cada push na `main` gera o APK em Releases → `teste` (`.github/workflows/android.yml`). Erros de compilação
  aparecem como anotações do job (`gh api repos/EldoFarias/prisma-tower/check-runs/<job>/annotations`).
- Debug usa sempre IDs de **teste** do AdMob (pacote `com.farias.prismatower.debug`); release lê os IDs reais dos
  secrets `ADMOB_*` e **falha** se faltar algum ou se for ID de teste. `applicationId` = `com.farias.prismatower`.
- Teste das fases na JVM (sem Android), da raiz:
  ```bash
  P=app/src/main/java/com/farias/prismatower/game
  CP=$(ls /opt/gradle/lib/*.jar | tr '\n' ':'); java -cp "$CP" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -cp "$CP" $P/Tower.kt tools/TowerTest.kt -d /tmp/tt && java -cp "$CP:/tmp/tt" TowerTestKt
  ```
- Atalhos só no debug: `adb shell am start -n com.farias.prismatower.debug/com.farias.prismatower.MainActivity --es screen game --ei level 12`
  (`home`, `chest`, `game`, `daily`, `shop`, `stats`).

## Regras do jogo (`game/Tower.kt`, puro Kotlin, testável na JVM)

- Anéis de 12 fatias (`SOLID`, `GAP`, `DANGER`), espaçados `Tower.SPACING`. A bola fica parada no ângulo 0 (de frente
  para a câmera) no raio `BALL_PATH_R`; quem gira é a torre (`TowerSim.rotation`). Ângulo θ = (r·sinθ, y, r·cosθ).
- Física fixa (`GRAVITY`, `BOUNCE_V`, passos de 1/120 s). Cair num vão = passa (combo +1). Cair no sólido = quica.
  Vermelho = morre. Com combo ≥ `FIRE_AT` (3) a bola pega fogo e quebra o próximo anel, até vermelho.
- Movimento dos anéis (`Motion.SPIN`/`SWING`) a partir da fase 12. Último anel = meta dourada (`goal`).
- `TowerLevels.level(n)` determinístico; dificuldade sobe até a fase ~120; cada fase é refeita até o `TowerBot`
  vencer a 5,5 rad/s (velocidade humana) — por isso toda fase tem solução. `daily(dia)` igual para todos no dia.
- Continuar por vídeo (`revive`) uma vez por fase: o anel que matou é quebrado.
- Pontos acumulam enquanto o jogador vence fases seguidas; zera ao perder (recorde em `Progress.bestScore`).

## Arquitetura

| Arquivo | O que tem |
|---|---|
| `game/Tower.kt` | Regras, gerador de fases, `TowerSim`, `TowerBot`. |
| `game/TowerGame.kt` | Roda na thread do GL: modo demo (menu) e jogo, câmera, tremida, respingos, anéis quebrando, partículas, rastro. |
| `game/GameHost.kt` | Ponte GL ↔ Compose: estado da partida para a UI, sons, vibração, textos flutuantes, missões, moedas. Comandos vão por `queueEvent`. |
| `gl/TowerRenderer.kt` | Desenho: fundo aurora, pilar, anéis chanfrados, bola, respingos, halos, partículas (point sprites). |
| `gl/Shaders.kt`, `gl/Meshes.kt` | GLSL ES 3.0 (padrões do pilar e das bolas no shader) e geometria. Use `ss()` em vez de `smoothstep` (bordas invertidas são indefinidas). |
| `gl/Looks.kt` | As 10 torres e 13 bolas (cores, estilo, preço). |
| `ui/` | Splash, Home (sobre a torre demo), Game (HUD, arrastar, pausa/derrota/vitória), Shop (prévia 3D ao vivo), Stats, Rewards (missões, baú), Dialogs (configurações, banner). |
| `data/Progress.kt`, `data/Missions.kt` | Tudo que persiste (fase, moedas, torres/bolas, recordes, missões, baú, desafio). |
| `ads/AdsManager.kt` | AdMob + UMP. Intersticial a cada 2 partidas e 90 s, nunca antes de 4 vitórias. |
| `audio/` | `SoundManager` (inclui `BOUNCE`, `PASS`), `MusicPlayer`, `Haptics` (atributos de mídia — necessário no MIUI). |

O `TowerView` (GLSurfaceView com MSAA 4×) fica atrás de todas as telas; no menu roda a demo com o robô.

## Convenções

- Textos em 8 idiomas (en, pt, es, fr, de, tr, in, hi): editar `tools/strings.py` e rodar `python3 tools/strings.py app/src/main/res`.
- Sons: `python3 tools/make_sfx.py app/src/main/res/raw`.
- Gradientes radiais com raio 0 derrubam o app: proteger tamanhos < 1 px.
- Em tablet o app inteiro é desenhado maior (`Scaled` no MainActivity).

## Forma de trabalho

- Não tirar prints nem rodar no emulador: só publicar o APK (push na `main`); o Eldo testa no celular e manda prints.

## Pendências

1. Eldo testar no celular: sensação do arrasto (`TURN_PER_WIDTH` em `GameScreen.kt`), câmera, dificuldade, desempenho.
2. Ícone e arte da loja (`tools/make_art.py` ainda tem a arte do Prisma Jam), textos da loja (`store/listings.py`
   e `store/PUBLICAR.md` ainda são do Jam), prints com legendas nos idiomas.
3. Criar o app no AdMob, blocos reais e secrets `ADMOB_*`; keystore e secrets `KEYSTORE_*`; publicar.
4. `docs/privacy.html` publicado no GitHub Pages (link em `ui/Dialogs.kt`).
