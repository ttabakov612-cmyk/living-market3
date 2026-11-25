package com.livingmarket.routine;

public enum DayPhase {
    MORNING_SERVICE("routine.livingmarket.phase.morning_service", 0, 1500),
    FIELD_EVENT("routine.livingmarket.phase.field_event", 1500, 4500),
    WORKSTATION_MORNING("routine.livingmarket.phase.workstation_morning", 4500, 7000),
    TOWN_SQUARE("routine.livingmarket.phase.town_square", 7000, 9000),
    WORKSTATION_AFTERNOON("routine.livingmarket.phase.workstation_afternoon", 9000, 11000),
    EVENING_DINNER("routine.livingmarket.phase.evening_dinner", 11000, 11850),
    NIGHT_REST("routine.livingmarket.phase.night_rest", 11850, 24000);

    private final String translationKey;
    private final int startTick;
    private final int endTick;

    DayPhase(String translationKey, int startTick, int endTick) {
        this.translationKey = translationKey;
        this.startTick = startTick;
        this.endTick = endTick;
    }

    public String getTranslationKey() {
        return this.translationKey;
    }

    public int getStartTick() {
        return this.startTick;
    }

    public int getEndTick() {
        return this.endTick;
    }

    public static DayPhase fromDayTime(long dayTime) {
        long timeOfDay = Math.floorMod(dayTime, 24000L);
        if (timeOfDay < 1500L) {
            return MORNING_SERVICE;
        }
        if (timeOfDay < 4500L) {
            return FIELD_EVENT;
        }
        if (timeOfDay < 7000L) {
            return WORKSTATION_MORNING;
        }
        if (timeOfDay < 9000L) {
            return TOWN_SQUARE;
        }
        if (timeOfDay < 11000L) {
            return WORKSTATION_AFTERNOON;
        }
        if (timeOfDay < 11850L) {
            return EVENING_DINNER;
        }
        return NIGHT_REST;
    }
}
