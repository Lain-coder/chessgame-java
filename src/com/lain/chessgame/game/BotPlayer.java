package com.lain.chessgame.game;

import com.lain.chessgame.game.gamerule.GameState;
import com.lain.chessgame.piece.piecerule.MoveRule;
import com.lain.chessgame.piece.pieceset.Queen;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/** 一个轻量、无需第三方棋库的电脑棋手。Bot 固定执黑。 */
public final class BotPlayer {
    public enum Difficulty {
        EASY("简单 · 休闲"),
        MEDIUM("普通 · 会吃子"),
        HARD("困难 · 三层搜索");

        private final String displayName;

        Difficulty(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    private static final int[] PIECE_VALUES = {0, 100, 320, 330, 500, 20_000, 900};
    private static final int MATE_SCORE = 1_000_000;
    private final Random random = new Random();

    public GameController.Move chooseMove(GameController position, Difficulty difficulty) {
        List<GameController.Move> moves = position.getAllLegalMoves();
        if (moves.isEmpty()) return null;
        if (difficulty == Difficulty.EASY) return chooseEasy(position, moves);

        orderMoves(position, moves);
        int depth = difficulty == Difficulty.HARD ? 3 : 2;
        int bestScore = Integer.MAX_VALUE;
        List<GameController.Move> bestMoves = new ArrayList<>();
        for (GameController.Move move : moves) {
            if (Thread.currentThread().isInterrupted()) return moves.getFirst();
            GameController next = play(position, move);
            int score = minimax(next, depth - 1, Integer.MIN_VALUE + 1, Integer.MAX_VALUE - 1);
            if (score < bestScore) {
                bestScore = score;
                bestMoves.clear();
                bestMoves.add(move);
            } else if (score == bestScore) {
                bestMoves.add(move);
            }
        }
        return bestMoves.get(random.nextInt(bestMoves.size()));
    }

    private GameController.Move chooseEasy(GameController position, List<GameController.Move> moves) {
        // 简单难度偶尔看得到明显吃子，其余时候保持轻松、带随机性。
        if (random.nextDouble() < 0.38) {
            int bestCapture = 0;
            List<GameController.Move> captures = new ArrayList<>();
            for (GameController.Move move : moves) {
                int target = Math.abs(position.getBoard().getPiece(move.getToRow(), move.getToCol()));
                if (target > bestCapture) {
                    bestCapture = target;
                    captures.clear();
                    captures.add(move);
                } else if (target == bestCapture && target > 0) {
                    captures.add(move);
                }
            }
            if (!captures.isEmpty()) return captures.get(random.nextInt(captures.size()));
        }
        return moves.get(random.nextInt(moves.size()));
    }

    private int minimax(GameController position, int depth, int alpha, int beta) {
        if (depth == 0 || position.getGameState().isGameOver() || Thread.currentThread().isInterrupted()) {
            return evaluate(position);
        }
        List<GameController.Move> moves = position.getAllLegalMoves();
        if (moves.isEmpty()) return evaluate(position);
        orderMoves(position, moves);

        if (position.isWhiteTurn()) {
            int best = Integer.MIN_VALUE;
            for (GameController.Move move : moves) {
                best = Math.max(best, minimax(play(position, move), depth - 1, alpha, beta));
                alpha = Math.max(alpha, best);
                if (beta <= alpha) break;
            }
            return best;
        }

        int best = Integer.MAX_VALUE;
        for (GameController.Move move : moves) {
            best = Math.min(best, minimax(play(position, move), depth - 1, alpha, beta));
            beta = Math.min(beta, best);
            if (beta <= alpha) break;
        }
        return best;
    }

    private GameController play(GameController position, GameController.Move move) {
        GameController next = position.copyForSearch();
        next.move(move.getFromRow(), move.getFromCol(), move.getToRow(), move.getToCol(), Queen.WHITE_QUEEN);
        return next;
    }

    private void orderMoves(GameController position, List<GameController.Move> moves) {
        moves.sort(Comparator.comparingInt((GameController.Move move) ->
                Math.abs(position.getBoard().getPiece(move.getToRow(), move.getToCol()))).reversed());
    }

    /** 正数对白方有利，负数对黑方有利。 */
    private int evaluate(GameController position) {
        GameState.Result result = position.getGameState().getResult();
        if (result == GameState.Result.WHITE_WINS_BY_CHECKMATE) return MATE_SCORE;
        if (result == GameState.Result.BLACK_WINS_BY_CHECKMATE) return -MATE_SCORE;
        if (position.getGameState().isGameOver()) return 0;

        int score = 0;
        int[][] board = position.getBoard().getBoard();
        for (int row = 0; row < board.length; row++) {
            for (int col = 0; col < board[row].length; col++) {
                int piece = board[row][col];
                if (piece == 0) continue;
                int value = PIECE_VALUES[Math.abs(piece)];
                int centerBonus = Math.min(row, 7 - row) + Math.min(col, 7 - col);
                if (Math.abs(piece) == 1 || Math.abs(piece) == 2 || Math.abs(piece) == 3) value += centerBonus * 3;
                score += piece > 0 ? value : -value;
            }
        }
        if (MoveRule.isKingInCheck(board, true)) score -= 35;
        if (MoveRule.isKingInCheck(board, false)) score += 35;
        return score;
    }
}
