package com.educagame.service;

import com.educagame.model.GamePhase;
import com.educagame.model.GameSession;
import com.educagame.model.GameType;
import com.educagame.model.Player;
import org.jboss.logging.Logger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Combination game - Multi-stage game combining different game types.
 * Each stage can be a different game type for varied gameplay.
 */
@ApplicationScoped
public class CombinationEngine implements GameEngineInterface {

    private static final Logger LOG = Logger.getLogger(CombinationEngine.class);
    private static final int DEFAULT_TIME_MS = 30000;
    private static final int BASE_POINTS = 500;
    private static final int STAGE_BONUS = 200;

    @Inject
    DataLoaderService dataLoaderService;
    @Inject
    GameHistoryService gameHistoryService;
    
    @Inject
    RoletrandoEngine roletrandoEngine;
    @Inject
    QuizEngine quizEngine;
    @Inject
    MillionaireEngine millionaireEngine;
    @Inject
    KahootEngine kahootEngine;
    @Inject
    SurvivalEngine survivalEngine;
    @Inject
    SequencingEngine sequencingEngine;
    @Inject
    DetectiveEngine detectiveEngine;
    @Inject
    BuzzerEngine buzzerEngine;
    @Inject
    SensoryEngine sensoryEngine;
    @Inject
    BinaryEngine binaryEngine;

    @SuppressWarnings("unchecked")
    public void startGame(GameSession session) {
        if (session.getGameType() != GameType.COMBINATION || session.getPhase() != GamePhase.LOBBY) return;
        session.setPhase(GamePhase.COUNTDOWN);
        session.setCurrentTurnIndex(0);
        
        List<Map<String, Object>> gameStages = dataLoaderService.getCombinationStages(session.getTheme());
        if (gameStages == null) gameStages = createDefaultStages();

        Map<String, Object> payload = new ConcurrentHashMap<>();
        payload.put("gameStages", gameStages);
        payload.put("stageIndex", 0);
        payload.put("stageScores", new ArrayList<Map<String, Object>>());
        payload.put("totalScores", new HashMap<String, Integer>());
        payload.put("completedStages", new HashSet<Integer>());
        session.setGamePayload(payload);
        session.setRoundStartedAt(System.currentTimeMillis());
        LOG.infof("Combination game started in room %s with %d stages", session.getRoomId(), gameStages.size());
    }

    public void transitionToPlaying(GameSession session) {
        session.setPhase(GamePhase.PLAYING);
        startStage(session, 0);
    }

    @SuppressWarnings("unchecked")
    private void startStage(GameSession session, int stageIndex) {
        Map<String, Object> payload = (Map<String, Object>) session.getGamePayload();
        if (payload == null) return;
        List<Map<String, Object>> gameStages = (List<Map<String, Object>>) payload.get("gameStages");
        if (gameStages == null || stageIndex >= gameStages.size()) {
            endCombinationGame(session);
            return;
        }
        
        payload.put("stageIndex", stageIndex);
        
        Map<String, Object> stage = gameStages.get(stageIndex);
        GameType stageType = GameType.valueOf((String) stage.get("gameType"));
        String stageConfig = (String) stage.get("config");
        
        payload.put("currentStageType", stageType);
        payload.put("currentStageConfig", stageConfig);
        payload.put("stageStartTime", System.currentTimeMillis());
        
        // Initialize stage-specific data
        initializeStageData(session, stageType, stageConfig);
        
        LOG.infof("Starting combination stage %d: %s (%s)", stageIndex, stageType, stageConfig);
    }

    @SuppressWarnings("unchecked")
    private void initializeStageData(GameSession session, GameType stageType, String config) {
        Map<String, Object> payload = (Map<String, Object>) session.getGamePayload();
        
        // Create stage-specific payload
        Map<String, Object> stagePayload = new ConcurrentHashMap<>();
        
        switch (stageType) {
            case ROLETRANDO:
                initRoletrandoStage(stagePayload, config);
                break;
            case QUIZ_SPEED:
                initQuizStage(stagePayload, config);
                break;
            case QUIZ_INCREMENTAL:
                initMillionaireStage(stagePayload, config);
                break;
            case SURVIVAL:
                initSurvivalStage(stagePayload, config);
                break;
            case SEQUENCING:
                initSequencingStage(stagePayload, config);
                break;
            case DETECTIVE:
                initDetectiveStage(stagePayload, config);
                break;
            case BUZZER:
                initBuzzerStage(stagePayload, config);
                break;
            case SENSORY:
                initSensoryStage(stagePayload, config);
                break;
            case BINARY_DECISION:
                initBinaryStage(stagePayload, config);
                break;
        }
        
        payload.put("stagePayload", stagePayload);
    }

    @SuppressWarnings("unchecked")
    private void initRoletrandoStage(Map<String, Object> stagePayload, String config) {
        List<String> phrases = dataLoaderService.getPhrases(config);
        if (phrases == null) phrases = List.of("EDUCAGAME", "MULTIPLAYER", "LEARNING");
        
        Random random = new Random();
        String phrase = phrases.get(random.nextInt(phrases.size()));
        
        stagePayload.put("phrase", phrase);
        stagePayload.put("revealed", new HashSet<String>());
        stagePayload.put("segmentValue", 100);
    }

    @SuppressWarnings("unchecked")
    private void initQuizStage(Map<String, Object> stagePayload, String config) {
        List<Map<String, Object>> questions = dataLoaderService.getQuizQuestions(config);
        if (questions == null || questions.isEmpty()) {
            questions = createDefaultQuizQuestions();
        }
        
        stagePayload.put("questions", questions);
        stagePayload.put("questionIndex", 0);
        stagePayload.put("responses", new HashMap<String, Map<String, Object>>());
    }

    @SuppressWarnings("unchecked")
    private void initMillionaireStage(Map<String, Object> stagePayload, String config) {
        stagePayload.put("level", 1);
        stagePayload.put("guaranteedPrize", 0);
        stagePayload.put("lifeline50Used", false);
        stagePayload.put("lifelineUniUsed", false);
        stagePayload.put("lifelineSkipUsed", false);
    }

    @SuppressWarnings("unchecked")
    private void initSurvivalStage(Map<String, Object> stagePayload, String config) {
        List<String> phrases = dataLoaderService.getPhrases(config);
        if (phrases == null) phrases = List.of("MYSTERY WORD", "HIDDEN PHRASE");
        
        String phrase = phrases.get(new Random().nextInt(phrases.size()));
        stagePayload.put("originalPhrase", phrase);
        stagePayload.put("maskedPhrase", createMaskedPhrase(phrase));
        stagePayload.put("revealedLetters", new HashSet<Character>());
    }

    @SuppressWarnings("unchecked")
    private void initSequencingStage(Map<String, Object> stagePayload, String config) {
        stagePayload.put("sequenceType", "time");
        stagePayload.put("items", Arrays.asList("1500", "1822", "1889", "1939"));
        stagePayload.put("correctOrder", Arrays.asList("1500", "1822", "1889", "1939"));
    }

    @SuppressWarnings("unchecked")
    private void initDetectiveStage(Map<String, Object> stagePayload, String config) {
        stagePayload.put("answer", "Albert Einstein");
        stagePayload.put("clues", Arrays.asList("German physicist", "E=mc²", "Nobel Prize", "Relativity theory"));
        stagePayload.put("revealedClues", new ArrayList<String>());
    }

    @SuppressWarnings("unchecked")
    private void initBuzzerStage(Map<String, Object> stagePayload, String config) {
        stagePayload.put("question", "What is 2 + 2?");
        stagePayload.put("options", Arrays.asList("3", "4", "5", "6"));
        stagePayload.put("correctIndex", 1);
        stagePayload.put("buzzOrder", new ArrayList<String>());
    }

    @SuppressWarnings("unchecked")
    private void initSensoryStage(Map<String, Object> stagePayload, String config) {
        stagePayload.put("sensoryType", "sound");
        stagePayload.put("answer", "Dog Barking");
        stagePayload.put("difficulty", 1);
    }

    @SuppressWarnings("unchecked")
    private void initBinaryStage(Map<String, Object> stagePayload, String config) {
        stagePayload.put("statement", "The Earth is flat");
        stagePayload.put("isTrue", false);
        stagePayload.put("explanation", "The Earth is spherical");
    }

    /**
     * Submit action for current stage.
     */
    @SuppressWarnings("unchecked")
    public boolean submitStageAction(GameSession session, String connectionId, Map<String, Object> action) {
        if (session.getGameType() != GameType.COMBINATION || session.getPhase() != GamePhase.PLAYING) return false;
        
        Map<String, Object> payload = (Map<String, Object>) session.getGamePayload();
        if (payload == null) return false;
        
        GameType currentStageType = (GameType) payload.get("currentStageType");
        Map<String, Object> stagePayload = (Map<String, Object>) payload.get("stagePayload");
        if (stagePayload == null) return false;
        
        boolean success = false;
        int points = 0;
        
        switch (currentStageType) {
            case ROLETRANDO:
                success = handleRoletrandoAction(session, connectionId, action, stagePayload);
                points = success ? BASE_POINTS + STAGE_BONUS : 0;
                break;
            case QUIZ_SPEED:
                success = handleQuizAction(session, connectionId, action, stagePayload);
                points = success ? BASE_POINTS + STAGE_BONUS : 0;
                break;
            case QUIZ_INCREMENTAL:
                success = handleMillionaireAction(session, connectionId, action, stagePayload);
                points = success ? BASE_POINTS + STAGE_BONUS : 0;
                break;
            case SURVIVAL:
                success = handleSurvivalAction(session, connectionId, action, stagePayload);
                points = success ? BASE_POINTS + STAGE_BONUS : 0;
                break;
            case SEQUENCING:
                success = handleSequencingAction(session, connectionId, action, stagePayload);
                points = success ? BASE_POINTS + STAGE_BONUS : 0;
                break;
            case DETECTIVE:
                success = handleDetectiveAction(session, connectionId, action, stagePayload);
                points = success ? BASE_POINTS + STAGE_BONUS : 0;
                break;
            case BUZZER:
                success = handleBuzzerAction(session, connectionId, action, stagePayload);
                points = success ? BASE_POINTS + STAGE_BONUS : 0;
                break;
            case SENSORY:
                success = handleSensoryAction(session, connectionId, action, stagePayload);
                points = success ? BASE_POINTS + STAGE_BONUS : 0;
                break;
            case BINARY_DECISION:
                success = handleBinaryAction(session, connectionId, action, stagePayload);
                points = success ? BASE_POINTS + STAGE_BONUS : 0;
                break;
        }
        
        // Update player score
        if (success) {
            Player player = session.getPlayers().stream().filter(p -> p.getId().equals(connectionId)).findFirst().orElse(null);
            if (player != null && !player.isBot()) {
                player.addScore(points);
                
                Map<String, Integer> totalScores = (Map<String, Integer>) payload.get("totalScores");
                if (totalScores == null) totalScores = new HashMap<>();
                totalScores.put(connectionId, totalScores.getOrDefault(connectionId, 0) + points);
                payload.put("totalScores", totalScores);
                
                LOG.infof("Combination stage action from %s: %s (%d points)", player.getName(), currentStageType, points);
            }
        }
        
        return success;
    }

    // Simplified handlers for each stage type
    private boolean handleRoletrandoAction(GameSession session, String connectionId, Map<String, Object> action, Map<String, Object> stagePayload) {
        // Simplified roletrando logic
        String guess = (String) action.get("guess");
        String phrase = (String) stagePayload.get("phrase");
        return guess != null && guess.equalsIgnoreCase(phrase);
    }

    private boolean handleQuizAction(GameSession session, String connectionId, Map<String, Object> action, Map<String, Object> stagePayload) {
        // Simplified quiz logic
        Integer answerIndex = (Integer) action.get("answerIndex");
        return answerIndex != null && answerIndex == 0; // Assume first option is correct
    }

    private boolean handleMillionaireAction(GameSession session, String connectionId, Map<String, Object> action, Map<String, Object> stagePayload) {
        // Simplified millionaire logic
        Integer answerIndex = (Integer) action.get("answerIndex");
        return answerIndex != null && answerIndex == 0;
    }

    private boolean handleSurvivalAction(GameSession session, String connectionId, Map<String, Object> action, Map<String, Object> stagePayload) {
        // Simplified survival logic
        String answer = (String) action.get("answer");
        String phrase = (String) stagePayload.get("originalPhrase");
        return answer != null && answer.equalsIgnoreCase(phrase);
    }

    private boolean handleSequencingAction(GameSession session, String connectionId, Map<String, Object> action, Map<String, Object> stagePayload) {
        // Simplified sequencing logic
        List<String> orderedIds = (List<String>) action.get("orderedIds");
        List<String> correctOrder = (List<String>) stagePayload.get("correctOrder");
        return orderedIds != null && orderedIds.equals(correctOrder);
    }

    private boolean handleDetectiveAction(GameSession session, String connectionId, Map<String, Object> action, Map<String, Object> stagePayload) {
        // Simplified detective logic
        String guess = (String) action.get("guess");
        String answer = (String) stagePayload.get("answer");
        return guess != null && guess.equalsIgnoreCase(answer);
    }

    private boolean handleBuzzerAction(GameSession session, String connectionId, Map<String, Object> action, Map<String, Object> stagePayload) {
        // Simplified buzzer logic
        Integer answerIndex = (Integer) action.get("answerIndex");
        Integer correctIndex = (Integer) stagePayload.get("correctIndex");
        return answerIndex != null && answerIndex.equals(correctIndex);
    }

    private boolean handleSensoryAction(GameSession session, String connectionId, Map<String, Object> action, Map<String, Object> stagePayload) {
        // Simplified sensory logic
        String guess = (String) action.get("guess");
        String answer = (String) stagePayload.get("answer");
        return guess != null && guess.equalsIgnoreCase(answer);
    }

    private boolean handleBinaryAction(GameSession session, String connectionId, Map<String, Object> action, Map<String, Object> stagePayload) {
        // Simplified binary logic
        Boolean decision = (Boolean) action.get("decision");
        Boolean isTrue = (Boolean) stagePayload.get("isTrue");
        return decision != null && decision.equals(isTrue);
    }

    /**
     * Move to next stage or end game.
     */
    @SuppressWarnings("unchecked")
    public void nextStage(GameSession session) {
        Map<String, Object> payload = (Map<String, Object>) session.getGamePayload();
        if (payload == null) return;
        
        int idx = payload.get("stageIndex") != null ? ((Number) payload.get("stageIndex")).intValue() : 0;
        List<Map<String, Object>> gameStages = (List<Map<String, Object>>) payload.get("gameStages");
        
        Set<Integer> completedStages = (Set<Integer>) payload.get("completedStages");
        if (completedStages == null) completedStages = new HashSet<>();
        completedStages.add(idx);
        payload.put("completedStages", completedStages);
        
        if (gameStages != null && idx + 1 < gameStages.size()) {
            startStage(session, idx + 1);
        } else {
            endCombinationGame(session);
        }
    }

    @SuppressWarnings("unchecked")
    private void endCombinationGame(GameSession session) {
        Map<String, Object> payload = (Map<String, Object>) session.getGamePayload();
        if (payload == null) return;
        
        session.setPhase(GamePhase.GAME_END);
        payload.put("finalRanking", buildCombinationRanking(session));
        gameHistoryService.recordGame(session);
        
        LOG.infof("Combination game ended in room %s", session.getRoomId());
    }

    private List<Map<String, Object>> buildCombinationRanking(GameSession session) {
        return session.getPlayers().stream()
                .filter(p -> !p.isBot())
                .sorted((a, b) -> Integer.compare(b.getScore(), a.getScore()))
                .map(p -> Map.<String, Object>of("id", p.getId(), "name", p.getName(), "score", p.getScore()))
                .toList();
    }

    // Helper methods
    private String createMaskedPhrase(String phrase) {
        return phrase.replaceAll("[A-Za-z]", "_");
    }

    private List<Map<String, Object>> createDefaultQuizQuestions() {
        List<Map<String, Object>> questions = new ArrayList<>();
        Map<String, Object> q = new HashMap<>();
        q.put("question", "Sample question?");
        q.put("options", Arrays.asList("Answer 1", "Answer 2", "Answer 3", "Answer 4"));
        q.put("correctIndex", 0);
        questions.add(q);
        return questions;
    }

    private List<Map<String, Object>> createDefaultStages() {
        List<Map<String, Object>> stages = new ArrayList<>();
        
        // Create a mix of different game types
        String[] gameTypes = {"ROLETRANDO", "QUIZ_SPEED", "SURVIVAL", "SEQUENCING", "BINARY_DECISION"};
        
        for (int i = 0; i < Math.min(5, gameTypes.length); i++) {
            Map<String, Object> stage = new HashMap<>();
            stage.put("gameType", gameTypes[i]);
            stage.put("config", "default");
            stage.put("timeLimitMs", DEFAULT_TIME_MS);
            stages.add(stage);
        }
        
        return stages;
    }

    @Override
    public boolean supports(GameType gameType) {
        return gameType == GameType.COMBINATION;
    }

    @Override
    public GameType getSupportedGameType() {
        return GameType.COMBINATION;
    }
}
