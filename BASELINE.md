# EducaGame - Baseline de Projeto

## Visão Geral
**Data**: 23/02/2026  
**Versão**: 1.0.0-SNAPSHOT  
**Status**: Funcional com testes passando

## Arquitetura

### Backend (Java 17 + Quarkus 3.31.4)
- **Framework**: Quarkus com suporte a WebSockets Next
- **Build**: Maven 3.9+
- **Testes**: JUnit 5 + RestAssured
- **Porta**: 8080 (API REST + WebSocket)

### Frontend (React 19 + TypeScript)
- **Framework**: React 19 com Vite
- **UI**: Material-UI v6 + Tailwind CSS
- **Testes E2E**: Playwright
- **Porta**: 5173 (dev)

## Funcionalidades Implementadas

### Jogos
1. **Roletrando (Roda a Roda)**
   - Roleta com valores e casas especiais
   - Sistema de turnos validado no servidor
   - Bots automáticos (até 3 jogadores)
   - Modos GUESS e SOLVE

2. **Quiz**
   - Perguntas síncronas multiplayer
   - Pontuação baseada em velocidade
   - Fases: Pergunta → Feedback → Ranking
   - Controle por host

3. **Show do Milhão**
   - 10 níveis de dificuldade
   - 3 lifelines: 50:50, Universitários, Pular
   - Sistema de prêmios garantidos

### Sistema
- **Salas multiplayer** com WebSocket em tempo real
- **Temas dinâmicos** com fallback para "default"
- **Estatísticas e histórico** de partidas
- **Administração** de temas e estatísticas

## Estrutura de Código

### Backend
```
com.educagame/
├── model/          # DTOs e entidades
├── service/        # Lógica de negócio
├── resource/       # REST + WebSocket
└── data/          # Arquivos JSON de temas
```

### Frontend
```
src/
├── hooks/          # useWebSocket, useSound
├── components/     # Componentes reutilizáveis
├── pages/         # Páginas da aplicação
└── types/         # TypeScript definitions
```

## Qualidade e Testes

### Testes Automáticos
- ✅ **Backend**: Compilação e testes unitários passando
- ✅ **Frontend E2E**: 3/3 testes Playwright passando
- ✅ **Integração**: WebSocket + REST API funcionando

### Código Quality
- **TypeScript**: Tipagem completa no frontend
- **Java**: Clean architecture com injeção de dependências
- **Segurança**: CORS configurado, headers de segurança

## Performance
- **Backend**: Quarkus com startup rápido e baixo consumo
- **Frontend**: Vite com HMR e build otimizado
- **WebSocket**: Comunicação em tempo real eficiente

## Deploy
- **Backend**: `mvn package` → JAR standalone
- **Frontend**: `npm run build` → estáticos
- **Produção**: Variáveis de ambiente para URLs

## Próximos Passos
1. Configurar CI/CD
2. Adicionar mais testes de integração
3. Implementar persistência (banco de dados)
4. Adicionar autenticação de usuários
5. Expandir temas e jogos

## Métricas Atuais
- **Arquivos Java**: 28 arquivos
- **Arquivos TypeScript**: 1.677 arquivos (incluindo node_modules)
- **Arquivos JSON**: 635 arquivos (incluindo node_modules)
- **Linhas de código**: ~3.464 (Java + TypeScript)
- **Cobertura de testes**: Backend unitário + Frontend E2E
- **Performance**: <2s startup backend

## Dependências Principais
- **Backend**: Quarkus, Jackson, JUnit 5, RestAssured
- **Frontend**: React 19, MUI v6, Playwright, Axios

---
*Baseline gerado automaticamente em 23/02/2026*
