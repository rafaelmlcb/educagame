# EducaGame

Ecossistema de jogos educacionais multiplayer com tempo real, temas dinâmicos e arquitetura moderna.

## Stack

- **Backend:** Java 17+, Quarkus 3.31.4, Maven, WebSockets Next, Jackson, JUnit 5, RestAssured
- **Frontend:** React 19, TypeScript, Vite, MUI v6, Tailwind CSS, Framer Motion, Lucide React, Axios, Playwright

## Pré-requisitos

- JDK 17+
- Node.js 18+
- Maven 3.9+

## Executar

### Backend

```bash
cd backend
mvn quarkus:dev
```

API em `http://localhost:8080`. WebSocket em `ws://localhost:8080/game`.

### Frontend

```bash
cd frontend
npm install
npm run dev
```

App em `http://localhost:5173`. O proxy do Vite encaminha `/api` e `/game` para o backend.

### Produção

- Backend: `mvn package` → `java -jar target/quarkus-app/quarkus-run.jar`
- Frontend: defina `VITE_API_URL` e `VITE_WS_URL` com a URL do backend em produção (ex.: `https://api.seudominio.com` e `wss://api.seudominio.com`); `npm run build` e sirva a pasta `dist` (Nginx, CDN ou estático).
- CORS: configure `CORS_ORIGINS` no backend com a origem do frontend (ex.: `https://seudominio.com`).
- Headers de segurança (CSP, HSTS, X-Frame-Options) já configurados em `application.properties`; ajuste CSP se usar recursos externos.

## Estrutura

- **Backend:** `com.educagame.model` (DTOs, GameResult), `com.educagame.service` (RoomManager, GameEngine, RoletrandoEngine, QuizEngine, MillionaireEngine, DataLoaderService, GameHistoryService, RoletrandoBotScheduler), `com.educagame.resource` (REST + WebSocket + Stats). Temas em `src/main/resources/data/{theme}/` (wheel.json, quiz.json, millionaire.json, phrases.json) com fallback para `default`.
- **Frontend:** `src/hooks` (useWebSocket, useSound), `src/components` (Roleta, Placar, RoletrandoPhrase, QuizQuestionCard, QuizLeaderboard, MillionaireBoard), `src/pages` (Home, GameRoom, ThemeSelect, AdminStats).

## Funcionalidades

- **Roletrando (ROLETRANDO):** Roleta com valores e casas especiais (Perde vez, Perde Tudo), GUESS/SOLVE, turnos validados no servidor, bots que preenchem até 3 jogadores.
- **Quiz (velocidade) (QUIZ_SPEED):** Perguntas síncronas, pontuação por velocidade.
- **Quiz (incremental) / Show do Milhão (QUIZ_INCREMENTAL):** 10 níveis, lifelines 50:50, Universitários e Pular, prêmio garantido.
- **Sobrevivência / Acerte ou Caia (SURVIVAL):** completar frase/palavra (vidas por jogador).
- **Ordenação (SEQUENCING):** organizar itens em sequência lógica.
- **Detetive (DETECTIVE):** dedução progressiva.
- **Buzzer (BUZZER):** primeiro a buzinar responde sob pressão.
- **Sensorial (SENSORY):** identificar mídia/descrição.
- **Decisão binária (BINARY_DECISION):** verdadeiro/falso.
- **Combinação (COMBINATION):** jogo por estágios combinando tipos.
- **Histórico e estatísticas:** Partidas registradas em memória; `/api/stats/summary` e `/api/stats/leaderboard?mode=...`.

## Segurança

- CORS restrito às origens configuradas (`CORS_ORIGINS`).
- Headers: X-Frame-Options, X-Content-Type-Options, X-XSS-Protection, HSTS, CSP.
- Validação de nomes, IDs, letra de palpite e frase (regex + limites).

## Testes

- Backend: `cd backend && mvn test`
- E2E (Playwright): `cd frontend && npm run test:e2e`

### E2E (como funciona)

- O Playwright usa `globalSetup`/`globalTeardown` para:
  - buildar o backend com Maven
  - iniciar o backend via JAR (`java -jar target/quarkus-app/quarkus-run.jar`)
  - aguardar o healthcheck (`/q/health`)

### Debug de falhas E2E

- Trace/vídeo/screenshot são mantidos em falhas.
- Para inspecionar um trace:

```bash
cd frontend
npx playwright show-trace test-results/<pasta-do-teste>/trace.zip
```

## Debug/Tracing (dev)

### Correlation ID (REST)

- O frontend envia um header `X-Request-Id` em todas as chamadas HTTP (Axios).
- O backend ecoa o mesmo header na resposta e inclui `requestId=...` em todos os logs do Quarkus via MDC.

Isso facilita correlacionar:

- Log do browser `http:request`/`http:response`
- Access log do Quarkus
- Logs de `com.educagame.*`

### Níveis de log (frontend)

Você pode controlar o nível via env:

```bash
VITE_LOG_LEVEL=debug npm run dev
```

Valores suportados: `debug`, `info`, `warn`, `error`.

### WebSocket (dev)

- WS do jogo: `ws://localhost:8080/game`.
- Em `dev`, o React `StrictMode` pode montar/desmontar componentes 2x e gerar tentativas de conexão/fechamento rápidas; os logs `ws:*` ajudam a enxergar isso.

### Diagnóstico rápido: ECONNREFUSED no proxy do Vite

Se aparecer `http proxy error ... ECONNREFUSED 127.0.0.1:8080`:

- Confirme que o backend está rodando:

```bash
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/q/health
```

- Confirme que o endpoint está acessível:

```bash
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/api/rooms
```

- Garanta que você não tem múltiplos `npm run dev` rodando ao mesmo tempo.

## Warnings e Ruído no Console

### Backend: Warnings do Compilador
- **Unchecked operations**: Suprimidos com `@SuppressWarnings("unchecked")` em `SurvivalEngine.java`.
- **system modules path not set**: Opcional; pode ser ignorado ou adicionado `--release 17` ao Maven se desejado.

### Frontend: Ruído de Extensões do Browser
Mensagens como `stats:1 listener indicated async response` no console do navegador são ruído de extensões (ex: DevTools, ad blockers) e não afetam o app. Podem ser ignoradas.
