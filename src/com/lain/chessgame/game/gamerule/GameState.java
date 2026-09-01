package com.lain.chessgame.game.gamerule;

import java.util.HashMap;
import java.util.Map;

/**
 * 记录一盘棋的状态，并统一处理各种和棋结果。
 * <p>
 * 坐标约定与 {@code Board} 一致：棋盘数组中正数为白方，负数为黑方，
 * 棋子类型的绝对值依次为兵 1、马 2、象 3、车 4、王 5、后 6。
 */
public class GameState {

    /** 用于悔棋时完整恢复对局状态。 */
    public static final class Snapshot {
        private final Map<String, Integer> positionCounts;
        private final Result result;
        private final Player drawOfferer;
        private final int halfMoveClock;

        private Snapshot(Map<String, Integer> positionCounts, Result result,
                         Player drawOfferer, int halfMoveClock) {
            this.positionCounts = positionCounts;
            this.result = result;
            this.drawOfferer = drawOfferer;
            this.halfMoveClock = halfMoveClock;
        }
    }

    /** 对局当前结果。 */
    public enum Result {
        IN_PROGRESS,
        DRAW_OFFERED_BY_WHITE,
        DRAW_OFFERED_BY_BLACK,
        DRAW_BY_AGREEMENT,
        DRAW_BY_FIFTY_MOVE_RULE,
        DRAW_BY_STALEMATE,
        DRAW_BY_INSUFFICIENT_MATERIAL,
        DRAW_BY_THREEFOLD_REPETITION,
        WHITE_WINS_BY_CHECKMATE,
        BLACK_WINS_BY_CHECKMATE
    }

    public enum Player {
        WHITE, BLACK
    }

    private static final int FIFTY_MOVE_HALF_MOVES = 100;
    private static final int THREEFOLD_REPETITION_COUNT = 3;

    private final Map<String, Integer> positionCounts = new HashMap<>();
    private Result result = Result.IN_PROGRESS;
    private Player drawOfferer;
    private int halfMoveClock;

    public Result getResult() {
        return result;
    }

    public boolean isGameOver() {
        return result != Result.IN_PROGRESS
                && result != Result.DRAW_OFFERED_BY_WHITE
                && result != Result.DRAW_OFFERED_BY_BLACK;
    }

    public int getHalfMoveClock() {
        return halfMoveClock;
    }

    public Player getDrawOfferer() {
        return drawOfferer;
    }

    public Snapshot createSnapshot() {
        return new Snapshot(new HashMap<>(positionCounts), result, drawOfferer, halfMoveClock);
    }

    public void restore(Snapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        positionCounts.clear();
        positionCounts.putAll(snapshot.positionCounts);
        result = snapshot.result;
        drawOfferer = snapshot.drawOfferer;
        halfMoveClock = snapshot.halfMoveClock;
    }

    /** 提出和棋；已有和棋提议或对局结束时不会覆盖现有状态。 */
    public boolean offerDraw(Player player) {
        if (player == null || result != Result.IN_PROGRESS) {
            return false;
        }

        drawOfferer = player;
        result = player == Player.WHITE
                ? Result.DRAW_OFFERED_BY_WHITE
                : Result.DRAW_OFFERED_BY_BLACK;
        return true;
    }

    /** 仅允许由提出方的对手接受和棋。 */
    public boolean acceptDraw(Player player) {
        if (drawOfferer == null || player == null || player == drawOfferer || isGameOver()) {
            return false;
        }

        result = Result.DRAW_BY_AGREEMENT;
        drawOfferer = null;
        return true;
    }

    /** 拒绝当前和棋提议，让对局恢复进行。 */
    public void declineDraw() {
        if (result == Result.DRAW_OFFERED_BY_WHITE || result == Result.DRAW_OFFERED_BY_BLACK) {
            result = Result.IN_PROGRESS;
            drawOfferer = null;
        }
    }

    /**
     * 每走完一步后调用一次。
     *
     * @param captured 是否吃子
     * @param pawnMoved 是否移动了兵
     * @param positionKey 当前局面的唯一标识（应包含棋子布局、轮到哪方走、王车易位权和过路兵信息）
     */
    public void recordMove(boolean captured, boolean pawnMoved, String positionKey) {
        if (isGameOver()) {
            return;
        }

        declineDraw();
        halfMoveClock = (captured || pawnMoved) ? 0 : halfMoveClock + 1;

        if (halfMoveClock >= FIFTY_MOVE_HALF_MOVES) {
            result = Result.DRAW_BY_FIFTY_MOVE_RULE;
            return;
        }

        recordPosition(positionKey);
    }

    /** 记录初始局面或走棋后的局面，用于三次重复判定。 */
    public void recordPosition(String positionKey) {
        if (isGameOver() || positionKey == null || positionKey.trim().isEmpty()) {
            return;
        }

        int count = positionCounts.getOrDefault(positionKey, 0) + 1;
        positionCounts.put(positionKey, count);
        if (count >= THREEFOLD_REPETITION_COUNT) {
            result = Result.DRAW_BY_THREEFOLD_REPETITION;
        }
    }

    /** 无合法走法且未被将军时，由 CheckRule / CheckmateRule 调用。 */
    public void declareStalemate() {
        if (!isGameOver()) {
            result = Result.DRAW_BY_STALEMATE;
            drawOfferer = null;
        }
    }

    /** 棋盘只剩无法将死的子力时调用。 */
    public void declareInsufficientMaterialDraw() {
        if (!isGameOver()) {
            result = Result.DRAW_BY_INSUFFICIENT_MATERIAL;
            drawOfferer = null;
        }
    }

    public void declareCheckmate(Player winner) {
        if (!isGameOver() && winner != null) {
            result = winner == Player.WHITE
                    ? Result.WHITE_WINS_BY_CHECKMATE
                    : Result.BLACK_WINS_BY_CHECKMATE;
            drawOfferer = null;
        }
    }

    /**
     * 判断最常见的理论和棋局面：王对王、王单象/单马对王，或双方仅有象且所有象位于同色格。
     */
    public static boolean hasInsufficientMaterial(int[][] board) {
        if (board == null) {
            return false;
        }

        int knightCount = 0;
        int bishopCount = 0;
        Integer bishopSquareColor = null;

        for (int row = 0; row < board.length; row++) {
            if (board[row] == null) {
                return false;
            }
            for (int col = 0; col < board[row].length; col++) {
                int pieceType = Math.abs(board[row][col]);
                if (pieceType == 0 || pieceType == 5) {
                    continue;
                }
                if (pieceType == 1 || pieceType == 4 || pieceType == 6) {
                    return false;
                }
                if (pieceType != 2 && pieceType != 3) {
                    return false;
                }

                if (pieceType == 2) {
                    knightCount++;
                } else {
                    bishopCount++;
                    int squareColor = (row + col) % 2;
                    if (bishopSquareColor != null && bishopSquareColor != squareColor) {
                        return false;
                    }
                    bishopSquareColor = squareColor;
                }
            }
        }
        int minorPieceCount = knightCount + bishopCount;
        if (minorPieceCount <= 1) {
            return true;
        }
        // 只要存在马和象，或存在两匹及以上的马，就仍可能形成将死局面。
        return knightCount == 0 && bishopSquareColor != null;
    }
}
