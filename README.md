# EducaGame

Ecossistema de jogos educacionais multiplayer (Roletrando, Quiz, Show do Milhão) com tempo real, temas dinâmicos e arquitetura moderna.

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

- **Roletrando:** Roleta com valores e casas especiais (Perde vez, Perde Tudo), GUESS/SOLVE, turnos validados no servidor, bots que preenchem até 3 jogadores.
- **Quiz:** Perguntas síncronas, pontuação por velocidade, etapas (Pergunta → Feedback → Ranking) controladas pelo host.
- **Show do Milhão:** 10 níveis, lifelines 50:50, Universitários e Pular, prêmio garantido.
- **Histórico e estatísticas:** Partidas registradas em memória; `/api/stats/summary` e `/api/stats/leaderboard?mode=...`.

## Segurança

- CORS restrito às origens configuradas (`CORS_ORIGINS`).
- Headers: X-Frame-Options, X-Content-Type-Options, X-XSS-Protection, HSTS, CSP.
- Validação de nomes, IDs, letra de palpite e frase (regex + limites).

## Testes

- Backend: `cd backend && mvn test`
- E2E frontend: `cd frontend && npm run test:e2e`
