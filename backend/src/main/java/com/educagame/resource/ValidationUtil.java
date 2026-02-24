package com.educagame.resource;

import java.util.regex.Pattern;

/**
 * OWASP-oriented validation: whitelist regex and length limits.
 */
public final class ValidationUtil {

    private static final int ROOM_ID_MAX = 64;
    private static final int PLAYER_NAME_MAX = 32;
    private static final Pattern ROOM_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]+$");
    private static final Pattern PLAYER_NAME_PATTERN = Pattern.compile("^[\\p{L}\\p{N}\\s._-]+$");

    private ValidationUtil() {
    }

    public static boolean isValidRoomId(String roomId) {
        return roomId != null && roomId.length() <= ROOM_ID_MAX && ROOM_ID_PATTERN.matcher(roomId).matches();
    }

    public static boolean isValidPlayerName(String name) {
        return name != null && !name.isBlank() && name.length() <= PLAYER_NAME_MAX && PLAYER_NAME_PATTERN.matcher(name.trim()).matches();
    }

    public static boolean isValidGuessLetter(String letter) {
        return letter != null && letter.length() == 1 && Character.isLetter(letter.charAt(0));
    }

    public static boolean isValidSolvePhrase(String phrase) {
        return phrase != null && phrase.length() >= 1 && phrase.length() <= 200;
    }
}
