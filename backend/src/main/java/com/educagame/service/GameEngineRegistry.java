package com.educagame.service;

import com.educagame.model.GameSession;
import com.educagame.model.GameType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.annotation.PostConstruct;
import org.jboss.logging.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Registry for all game engines in EducaGame ecosystem.
 * Provides dynamic game engine discovery and management.
 */
@ApplicationScoped
public class GameEngineRegistry {
    
    private static final Logger LOG = Logger.getLogger(GameEngineRegistry.class);
    
    private final Map<GameType, GameEngineInterface> engines = new HashMap<>();
    
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
    
    @Inject
    CombinationEngine combinationEngine;
    
    /**
     * Initialize the registry with available game engines.
     * This method is called automatically after CDI injection.
     */
    @PostConstruct
    public void initialize() {
        registerEngine(roletrandoEngine);
        registerEngine(quizEngine);
        registerEngine(millionaireEngine);
        registerEngine(kahootEngine);
        registerEngine(survivalEngine);
        registerEngine(sequencingEngine);
        registerEngine(detectiveEngine);
        registerEngine(buzzerEngine);
        registerEngine(sensoryEngine);
        registerEngine(binaryEngine);
        registerEngine(combinationEngine);
        
        LOG.infof("GameEngineRegistry initialized with %d engines", engines.size());
    }
    
    /**
     * Register a game engine with the registry.
     * @param engine The game engine to register
     */
    public void registerEngine(GameEngineInterface engine) {
        if (engine != null && engine.getSupportedGameType() != null) {
            engines.put(engine.getSupportedGameType(), engine);
            LOG.infof("Registered engine for game type: %s", engine.getSupportedGameType());
        }
    }
    
    /**
     * Get the appropriate game engine for the given game type.
     * @param gameType The game type
     * @return Optional containing the engine if found
     */
    public Optional<GameEngineInterface> getEngine(GameType gameType) {
        return Optional.ofNullable(engines.get(gameType));
    }
    
    /**
     * Start a game using the appropriate engine.
     * @param session The game session to start
     * @return true if an engine was found and game started, false otherwise
     */
    public boolean startGame(GameSession session) {
        GameType gameType = session.getGameType();
        Optional<GameEngineInterface> engineOpt = getEngine(gameType);
        
        if (engineOpt.isPresent()) {
            engineOpt.get().startGame(session);
            LOG.infof("Started game of type %s in room %s", gameType, session.getRoomId());
            return true;
        } else {
            LOG.warnf("No engine found for game type: %s", gameType);
            return false;
        }
    }
    
    /**
     * Transition a game to playing state using the appropriate engine.
     * @param session The game session to transition
     * @return true if an engine was found and transition completed, false otherwise
     */
    public boolean transitionToPlaying(GameSession session) {
        GameType gameType = session.getGameType();
        Optional<GameEngineInterface> engineOpt = getEngine(gameType);
        
        if (engineOpt.isPresent()) {
            engineOpt.get().transitionToPlaying(session);
            LOG.infof("Transitioned game %s to playing in room %s", gameType, session.getRoomId());
            return true;
        } else {
            LOG.warnf("No engine found for game type: %s", gameType);
            return false;
        }
    }
    
    /**
     * Get all registered game types.
     * @return Array of supported game types
     */
    public GameType[] getSupportedGameTypes() {
        return engines.keySet().toArray(new GameType[0]);
    }
    
    /**
     * Check if a game type is supported.
     * @param gameType The game type to check
     * @return true if supported, false otherwise
     */
    public boolean isSupported(GameType gameType) {
        return engines.containsKey(gameType);
    }
}
