# Prisma Tower — contexto do projeto

Jogo 3D estilo "Helix Jump": o jogador arrasta o dedo para girar a torre e a bola quica de anel em anel até o fundo.
Android, só Play Store. Kotlin + Jetpack Compose (telas) + OpenGL ES 3.0 (`GLSurfaceView`, sem engine).
Dono: Eldo (EldoFarias). Comunicação em português. **Monetização só AdMob** (banner, intersticial, premiado) —
sem outras redes, sem assinatura, sem compras no app.

## Como compilar e rodar

- Cada push na `main` gera o AAB assinado de release (artefato `PrismaTower-release-aab` do Actions,
  `.github/workflows/android.yml`) para enviar à Play Store; não há mais APK em Releases. Erros de compilação
  aparecem como anotações do job (`gh api repos/EldoFarias/prisma-tower/check-runs/<job>/annotations`).
- Chave de upload: `Projetos/PrismaTower-Keystore` (fora do repo; senha no LEIA-ME de lá). Secrets `KEYSTORE_*` e
  `ADMOB_*` já cadastrados (app AdMob `ca-app-pub-1312827233427949~3200941962`).
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
- Obstáculos (a partir da fase, `TowerLevels.*_FROM`): moedas 3 (sobre um vão), vidro 15 (`GLASS`: racha no 1º quique,
  quebra no 2º), anéis que somem 22 (`Platform.blink`), portal 30 (`PORTAL`: desce 2 anéis). `TowerSim.typeAt()` dá o
  tipo efetivo no tempo; o robô usa isso. Escudo (`sim.shields`, por vídeo) salva de 1 vermelho.
- Torre chefe a cada 10 fases (`isBoss`): mais alta, +dificuldade, prêmio ×3, música épica.
- Queda livre (`TowerLevels.endless(seed)`): ~24 fases checadas coladas (~1000 anéis), recorde `Progress.bestEndless`;
  gerada em segundo plano (`LevelCache.prefetchEndless`).
- Câmera lenta (0,3×) quando a bola passa raspando num vermelho (`TowerEvent.*.near`), no máx. a cada 4 s.
- Pontos acumulam enquanto o jogador vence fases seguidas; zera ao perder (recorde em `Progress.bestScore`).

## Arquitetura

| Arquivo | O que tem |
|---|---|
| `game/Tower.kt` | Regras, gerador de fases, `TowerSim`, `TowerBot`. |
| `game/TowerGame.kt` | Roda na thread do GL: modo demo (menu) e jogo, câmera, tremida, respingos, anéis quebrando, partículas, rastro. |
| `game/GameHost.kt` | Ponte GL ↔ Compose: estado da partida para a UI, sons, vibração, textos flutuantes, missões, moedas. Comandos vão por `queueEvent`. |
| `gl/TowerRenderer.kt` | Desenho: fundo aurora, pilar, anéis chanfrados, bola, respingos, halos, partículas (point sprites). |
| `gl/Shaders.kt`, `gl/Meshes.kt` | GLSL ES 3.0 (padrões do pilar e das bolas no shader) e geometria. Use `ss()` em vez de `smoothstep` (bordas invertidas são indefinidas). |
| `gl/Looks.kt` | As 13 torres (com cenário `Env`: céu, base, nuvens, partículas) e 23 bolas; raridade pelo preço. |
| `data/Achievements.kt`, `data/Trails.kt` | Conquistas (contadores em `Progress`) e os 7 rastros da bola. |
| `ui/` | Splash, Home (sobre a torre demo), Game (HUD, arrastar, escudo, avisos, pausa/derrota/vitória), Shop (torres/bolas/rastros, prévia 3D, prêmio surpresa, teste por vídeo), Map (mundos de 10 fases), Extras (conquistas, roleta), Stats, Rewards (missões, baú), Dialogs. |
| `data/Progress.kt`, `data/Missions.kt` | Tudo que persiste (fase, moedas, torres/bolas, recordes, missões, baú, desafio). |
| `ads/AdsManager.kt` | AdMob + UMP. Intersticial a cada 2 partidas e 90 s, nunca antes de 4 vitórias. |
| `audio/` | `SoundManager` (inclui `BOUNCE`, `PASS`), `MusicPlayer`, `Haptics` (atributos de mídia — necessário no MIUI). |

O `TowerView` (GLSurfaceView com MSAA 4×) fica atrás de todas as telas; no menu roda a demo com o robô.

## Convenções

- Textos em 8 idiomas (en, pt, es, fr, de, tr, in, hi): editar `tools/strings.py` e rodar `python3 tools/strings.py app/src/main/res`.
- Sons: `python3 tools/make_sfx.py app/src/main/res/raw`. Músicas: `python3 tools/make_music.py app/src/main/res/raw/music_<estilo>.ogg <chill|sunny|epic|space>`
  (`MusicPlayer.trackFor` escolhe por torre). Ícone e arte da loja: `python3 tools/make_art.py .`; textos da loja: `python3 store/listings.py`.
- Gradientes radiais com raio 0 derrubam o app: proteger tamanhos < 1 px.
- Em tablet o app inteiro é desenhado maior (`Scaled` no MainActivity).
- **Desempenho é prioridade (o Eldo quer o jogo extremamente leve):** nada de alocar objetos no laço de desenho
  (use os arrays de rascunho do `TowerRenderer`); fatias dos anéis e halos são desenhados com instancing; o céu
  detalhado é desenhado em 1/3 da resolução a cada 2 quadros (fundo pesado em resolução cheia travou o jogo);
  "Cenário detalhado" nas configurações e desligamento automático se os quadros ficarem lentos; GL limitado a
  1080 px de largura; GL pausado na splash e nas estatísticas; fases pré-geradas em segundo plano (`LevelCache`);
  animações do Compose leem o tempo só dentro de `graphicsLayer`/`drawWithContent` (sem recompor a cada quadro).
- O shader iluminado tem 4 variantes (`Shaders.lit("V_RING"|"V_PILLAR"|"V_BALL"|"V_BASE")`, blocos `#ifdef`):
  um shader único para tudo deixava cada pixel pagar pelo caminho mais pesado. Uniforms comuns vão em `useLit()`.
- Shaders: validar com glslangValidator antes de enviar (npm `glslang-validator-prebuilt-predownloaded`).
  Uniform usado no vertex E no fragment precisa da MESMA precisão explícita (ex.: `uniform mediump int uInst;`):
  no Xiaomi do Eldo a diferença derruba o app ao abrir ("precision does not match").

## Forma de trabalho

- Não tirar prints nem rodar no emulador. O CI agora gera só o AAB de release (não há mais APK de teste em Releases);
  para o Eldo testar algo antes de publicar, combinar com ele (ex.: teste interno na Play Console).

## Publicação

- **Publicado na Play Store em 2026-10-31**, versão 1.0.1 (`versionName` em `app/build.gradle.kts`). O `versionCode`
  é o número da execução do Actions (`github.run_number`), então cada push gera um AAB com código maior.
- Próxima atualização: subir o `versionName` (1.0.2…), push na `main`, baixar o artefato `PrismaTower-release-aab` e enviar
  na Play Console.

## Pendências

1. Ideias para atualizações: ver com o Eldo (ele testou e aprovou a versão publicada).
