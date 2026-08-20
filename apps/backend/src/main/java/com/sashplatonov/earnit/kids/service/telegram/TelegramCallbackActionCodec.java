package com.sashplatonov.earnit.kids.service.telegram;

final class TelegramCallbackActionCodec {
    private TelegramCallbackActionCodec() {
    }

    static String compact(String action) {
        String[] coinParts = action.split("-", -1);
        if (isCoinAction(coinParts)) {
            return ("confirm".equals(coinParts[1]) ? "A" : "a")
                + ("add".equals(coinParts[2]) ? "+" : "-")
                + coinParts[3] + "-" + coinParts[5];
        }
        String[] parts = splitChildAction(action);
        String compactBase = compactBase(parts[0]);
        if (compactBase.equals(parts[0])) {
            return action;
        }
        return parts[1] == null ? compactBase : compactBase + "-" + parts[1];
    }

    static String expand(String action) {
        if (isCompactCoinAction(action)) {
            String[] parts = action.substring(2).split("-", -1);
            if (parts.length == 2) {
                return "coins-" + (action.charAt(0) == 'A' ? "confirm" : "apply") + "-"
                    + (action.charAt(1) == '+' ? "add" : "remove") + "-" + parts[0]
                    + "-child-" + parts[1];
            }
        }
        String[] parts = splitChildAction(action);
        String expandedBase = expandBase(parts[0]);
        return parts[1] == null ? expandedBase : expandedBase + "-child-" + parts[1];
    }

    private static boolean isCoinAction(String[] parts) {
        return parts.length == 6 && "coins".equals(parts[0])
            && ("confirm".equals(parts[1]) || "apply".equals(parts[1]))
            && ("add".equals(parts[2]) || "remove".equals(parts[2]))
            && "child".equals(parts[4]);
    }

    private static boolean isCompactCoinAction(String action) {
        return action.length() > 2 && (action.charAt(0) == 'A' || action.charAt(0) == 'a')
            && (action.charAt(1) == '+' || action.charAt(1) == '-');
    }

    private static String[] splitChildAction(String action) {
        int marker = action.indexOf("-child-");
        if (marker >= 0) {
            return new String[] {action.substring(0, marker), action.substring(marker + 7)};
        }
        if (action.length() > 2 && action.charAt(1) == '-' && isCompactBase(action.charAt(0))) {
            return new String[] {action.substring(0, 1), action.substring(2)};
        }
        return new String[] {action, null};
    }

    private static boolean isCompactBase(char base) {
        return switch (base) {
            case 'm', 't', 'r', 'q', 'o', 'e', 'h', 'n' -> true;
            default -> false;
        };
    }

    private static String compactBase(String base) {
        return switch (base) {
            case "main" -> "m";
            case "tasks" -> "t";
            case "rewards" -> "r";
            case "requests" -> "q";
            case "coins" -> "o";
            case "recent" -> "e";
            case "child" -> "h";
            case "noop" -> "n";
            default -> base;
        };
    }

    private static String expandBase(String base) {
        return switch (base) {
            case "m" -> "main";
            case "t" -> "tasks";
            case "r" -> "rewards";
            case "q" -> "requests";
            case "o" -> "coins";
            case "e" -> "recent";
            case "h" -> "child";
            case "n" -> "noop";
            default -> base;
        };
    }
}
