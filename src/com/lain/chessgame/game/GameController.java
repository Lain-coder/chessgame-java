package com.lain.chessgame.game;

import com.lain.chessgame.board.Board;
import com.lain.chessgame.game.gamerule.GameState;
import com.lain.chessgame.piece.piecerule.MoveRule;
import com.lain.chessgame.piece.pieceset.Queen;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/** 管理一盘棋的回合、合法走法和特殊走法；界面不直接修改 Board。 */
public class GameController {

    private static final class Snapshot {
        private final int[][] boardData;
        private final boolean whiteTurn;
        private final boolean whiteKingMoved, blackKingMoved, whiteLeftRookMoved, whiteRightRookMoved;
        private final boolean blackLeftRookMoved, blackRightRookMoved;
        private final int enPassantPawnRow, enPassantPawnCol;
        private final GameState.Snapshot gameStateSnapshot;

        private Snapshot(GameController game) {
            boardData = copyBoard(game.board.getBoard());
            whiteTurn = game.whiteTurn;
            whiteKingMoved = game.whiteKingMoved;
            blackKingMoved = game.blackKingMoved;
            whiteLeftRookMoved = game.whiteLeftRookMoved;
            whiteRightRookMoved = game.whiteRightRookMoved;
            blackLeftRookMoved = game.blackLeftRookMoved;
            blackRightRookMoved = game.blackRightRookMoved;
            enPassantPawnRow = game.enPassantPawnRow;
            enPassantPawnCol = game.enPassantPawnCol;
            gameStateSnapshot = game.gameState.createSnapshot();
        }
    }

    public static final class Square {
        private final int row;
        private final int col;

        public Square(int row, int col) {
            this.row = row;
            this.col = col;
        }

        public int getRow() {
            return row;
        }

        public int getCol() {
            return col;
        }
    }

    /** 一步完整走法，供界面和电脑棋手共享。 */
    public static final class Move {
        private final int fromRow;
        private final int fromCol;
        private final int toRow;
        private final int toCol;

        public Move(int fromRow, int fromCol, int toRow, int toCol) {
            this.fromRow = fromRow;
            this.fromCol = fromCol;
            this.toRow = toRow;
            this.toCol = toCol;
        }

        public int getFromRow() { return fromRow; }
        public int getFromCol() { return fromCol; }
        public int getToRow() { return toRow; }
        public int getToCol() { return toCol; }
    }

    private final Board board = new Board();
    private final GameState gameState = new GameState();
    private boolean whiteTurn = true;
    private boolean whiteKingMoved;
    private boolean blackKingMoved;
    private boolean whiteLeftRookMoved;
    private boolean whiteRightRookMoved;
    private boolean blackLeftRookMoved;
    private boolean blackRightRookMoved;
    private int enPassantPawnRow = -1;
    private int enPassantPawnCol = -1;
    private final Deque<Snapshot> moveSnapshots = new ArrayDeque<>();
    private final List<String> moveHistory = new ArrayList<>();
    private GameState.Player undoRequester;

    public GameController() {
        gameState.recordPosition(positionKey());
    }

    private GameController(GameController source) {
        board.setBoard(copyBoard(source.board.getBoard()));
        whiteTurn = source.whiteTurn;
        whiteKingMoved = source.whiteKingMoved;
        blackKingMoved = source.blackKingMoved;
        whiteLeftRookMoved = source.whiteLeftRookMoved;
        whiteRightRookMoved = source.whiteRightRookMoved;
        blackLeftRookMoved = source.blackLeftRookMoved;
        blackRightRookMoved = source.blackRightRookMoved;
        enPassantPawnRow = source.enPassantPawnRow;
        enPassantPawnCol = source.enPassantPawnCol;
        gameState.restore(source.gameState.createSnapshot());
        moveHistory.addAll(source.moveHistory);
        undoRequester = source.undoRequester;
    }

    /** 返回完全独立的对局副本，Bot 可以安全地在后台推演。 */
    public GameController copy() {
        return new GameController(this);
    }

    public Board getBoard() {
        return board;
    }

    public GameState getGameState() {
        return gameState;
    }

    public boolean isWhiteTurn() {
        return whiteTurn;
    }

    public boolean isCurrentPlayerPiece(int piece) {
        return piece != 0 && (piece > 0) == whiteTurn;
    }

    public boolean isCurrentPlayerInCheck() {
        return MoveRule.isKingInCheck(board.getBoard(), whiteTurn);
    }

    public List<String> getMoveHistory() {
        return Collections.unmodifiableList(moveHistory);
    }

    public boolean requestDraw() {
        return gameState.offerDraw(currentPlayer());
    }

    public boolean acceptDraw() {
        GameState.Player offerer = gameState.getDrawOfferer();
        return offerer != null && gameState.acceptDraw(opposite(offerer));
    }

    public void declineDraw() {
        gameState.declineDraw();
    }

    public boolean requestUndo() {
        if (moveSnapshots.isEmpty() || undoRequester != null) {
            return false;
        }
        // 请求者是刚刚走完上一步的一方；此时棋盘轮到其对手。
        undoRequester = opposite(currentPlayer());
        return true;
    }

    public boolean acceptUndo() {
        if (undoRequester == null || undoRequester == currentPlayer()) {
            return false;
        }
        restore(moveSnapshots.pop());
        if (!moveHistory.isEmpty()) {
            moveHistory.remove(moveHistory.size() - 1);
        }
        undoRequester = null;
        return true;
    }

    public void declineUndo() {
        undoRequester = null;
    }

    public GameState.Player getUndoRequester() {
        return undoRequester;
    }

    public List<Square> getLegalMoves(int fromRow, int fromCol) {
        if (gameState.isGameOver() || !isInside(fromRow, fromCol)
                || !isCurrentPlayerPiece(board.getPiece(fromRow, fromCol))) {
            return Collections.emptyList();
        }

        List<Square> moves = new ArrayList<>();
        for (int row = 0; row < Board.SIZE_X; row++) {
            for (int col = 0; col < Board.SIZE_Y; col++) {
                if (isLegalMove(fromRow, fromCol, row, col)) {
                    moves.add(new Square(row, col));
                }
            }
        }
        return moves;
    }

    /** 返回当前一方的全部合法走法。 */
    public List<Move> getAllLegalMoves() {
        if (gameState.isGameOver()) return Collections.emptyList();
        List<Move> moves = new ArrayList<>();
        for (int fromRow = 0; fromRow < Board.SIZE_X; fromRow++) {
            for (int fromCol = 0; fromCol < Board.SIZE_Y; fromCol++) {
                if (!isCurrentPlayerPiece(board.getPiece(fromRow, fromCol))) continue;
                for (Square target : getLegalMoves(fromRow, fromCol)) {
                    moves.add(new Move(fromRow, fromCol, target.getRow(), target.getCol()));
                }
            }
        }
        return moves;
    }

    /** 无需双方确认地撤销最后一步，供“悔棋”按钮使用。 */
    public boolean undoLastMove() {
        if (moveSnapshots.isEmpty()) return false;
        restore(moveSnapshots.pop());
        if (!moveHistory.isEmpty()) moveHistory.remove(moveHistory.size() - 1);
        undoRequester = null;
        return true;
    }

    public boolean move(int fromRow, int fromCol, int toRow, int toCol, int promotionPiece) {
        if (!isLegalMove(fromRow, fromCol, toRow, toCol)) {
            return false;
        }

        int[][] data = board.getBoard();
        int movingPiece = data[fromRow][fromCol];
        String notation = moveNotation(movingPiece, fromRow, fromCol, toRow, toCol,
                data[toRow][toCol] != 0 || isEnPassantMove(fromRow, fromCol, toRow, toCol));
        boolean pawnMoved = Math.abs(movingPiece) == 1;
        boolean enPassant = isEnPassantMove(fromRow, fromCol, toRow, toCol);
        boolean captured = data[toRow][toCol] != 0 || enPassant;
        moveSnapshots.push(new Snapshot(this));
        updateCastlingRightsBeforeMove(movingPiece, fromRow, fromCol, toRow, toCol);

        if (isCastlingMove(fromRow, fromCol, toRow, toCol)) {
            board.movePiece(fromRow, fromCol, toRow, toCol);
            int rookFromCol = toCol == 6 ? 7 : 0;
            int rookToCol = toCol == 6 ? 5 : 3;
            board.movePiece(fromRow, rookFromCol, fromRow, rookToCol);
        } else {
            board.movePiece(fromRow, fromCol, toRow, toCol);
            if (enPassant) {
                data[fromRow][toCol] = 0;
            }
        }

        if (pawnMoved && (toRow == 0 || toRow == 7)) {
            int promotedType = isPromotionPiece(promotionPiece) ? promotionPiece : Queen.WHITE_QUEEN;
            data[toRow][toCol] = movingPiece > 0 ? promotedType : -promotedType;
            notation += "=" + pieceLetter(promotedType);
        }

        enPassantPawnRow = pawnMoved && Math.abs(toRow - fromRow) == 2 ? toRow : -1;
        enPassantPawnCol = enPassantPawnRow == -1 ? -1 : toCol;
        whiteTurn = !whiteTurn;
        gameState.recordMove(captured, pawnMoved, positionKey());
        updateEndState();
        if (gameState.getResult() == GameState.Result.WHITE_WINS_BY_CHECKMATE
                || gameState.getResult() == GameState.Result.BLACK_WINS_BY_CHECKMATE) {
            notation += "#";
        } else if (MoveRule.isKingInCheck(data, whiteTurn)) {
            notation += "+";
        }
        moveHistory.add(notation);
        undoRequester = null;
        return true;
    }

    public boolean canPromote(int fromRow, int fromCol, int toRow) {
        int piece = isInside(fromRow, fromCol) ? board.getPiece(fromRow, fromCol) : 0;
        return Math.abs(piece) == 1 && (toRow == 0 || toRow == 7);
    }

    private boolean isLegalMove(int fromRow, int fromCol, int toRow, int toCol) {
        if (!isInside(fromRow, fromCol) || !isInside(toRow, toCol)) {
            return false;
        }
        int piece = board.getPiece(fromRow, fromCol);
        if (!isCurrentPlayerPiece(piece)) {
            return false;
        }

        if (isCastlingMove(fromRow, fromCol, toRow, toCol)) {
            return isLegalCastling(fromRow, toCol);
        }
        if (isEnPassantMove(fromRow, fromCol, toRow, toCol)) {
            return !leavesKingInCheckAfterEnPassant(fromRow, fromCol, toRow, toCol);
        }
        return MoveRule.isLegalMove(board.getBoard(), fromRow, fromCol, toRow, toCol)
                && !MoveRule.leavesKingInCheck(board.getBoard(), fromRow, fromCol, toRow, toCol);
    }

    private boolean isCastlingMove(int fromRow, int fromCol, int toRow, int toCol) {
        int piece = isInside(fromRow, fromCol) ? board.getPiece(fromRow, fromCol) : 0;
        return Math.abs(piece) == 5 && fromRow == toRow && fromCol == 4
                && (toCol == 2 || toCol == 6);
    }

    private boolean isLegalCastling(int row, int kingTargetCol) {
        boolean white = board.getPiece(row, 4) > 0;
        if ((white && (whiteKingMoved || row != 7)) || (!white && (blackKingMoved || row != 0))) {
            return false;
        }

        boolean kingSide = kingTargetCol == 6;
        int rookCol = kingSide ? 7 : 0;
        boolean rookMoved = white
                ? (kingSide ? whiteRightRookMoved : whiteLeftRookMoved)
                : (kingSide ? blackRightRookMoved : blackLeftRookMoved);
        int expectedRook = white ? 4 : -4;
        if (rookMoved || board.getPiece(row, rookCol) != expectedRook || MoveRule.isKingInCheck(board.getBoard(), white)) {
            return false;
        }

        int direction = kingSide ? 1 : -1;
        for (int col = 4 + direction; col != rookCol; col += direction) {
            if (board.getPiece(row, col) != 0) {
                return false;
            }
        }
        if (MoveRule.isSquareAttacked(board.getBoard(), row, 4 + direction, !white)
                || MoveRule.isSquareAttacked(board.getBoard(), row, kingTargetCol, !white)) {
            return false;
        }
        return true;
    }

    private boolean isEnPassantMove(int fromRow, int fromCol, int toRow, int toCol) {
        int piece = isInside(fromRow, fromCol) ? board.getPiece(fromRow, fromCol) : 0;
        int direction = piece > 0 ? -1 : 1;
        return Math.abs(piece) == 1 && board.getPiece(toRow, toCol) == 0
                && toRow - fromRow == direction && Math.abs(toCol - fromCol) == 1
                && fromRow == enPassantPawnRow && toCol == enPassantPawnCol;
    }

    private boolean leavesKingInCheckAfterEnPassant(int fromRow, int fromCol, int toRow, int toCol) {
        int[][] data = board.getBoard();
        int movingPiece = data[fromRow][fromCol];
        int capturedPawn = data[fromRow][toCol];
        data[toRow][toCol] = movingPiece;
        data[fromRow][fromCol] = 0;
        data[fromRow][toCol] = 0;
        boolean inCheck = MoveRule.isKingInCheck(data, movingPiece > 0);
        data[fromRow][fromCol] = movingPiece;
        data[fromRow][toCol] = capturedPawn;
        data[toRow][toCol] = 0;
        return inCheck;
    }

    private void updateCastlingRightsBeforeMove(int piece, int fromRow, int fromCol, int toRow, int toCol) {
        if (piece == 5) whiteKingMoved = true;
        if (piece == -5) blackKingMoved = true;
        if (fromRow == 7 && fromCol == 0) whiteLeftRookMoved = true;
        if (fromRow == 7 && fromCol == 7) whiteRightRookMoved = true;
        if (fromRow == 0 && fromCol == 0) blackLeftRookMoved = true;
        if (fromRow == 0 && fromCol == 7) blackRightRookMoved = true;
        // 若车在原始位置被吃，王车易位权也随之消失。
        if (toRow == 7 && toCol == 0) whiteLeftRookMoved = true;
        if (toRow == 7 && toCol == 7) whiteRightRookMoved = true;
        if (toRow == 0 && toCol == 0) blackLeftRookMoved = true;
        if (toRow == 0 && toCol == 7) blackRightRookMoved = true;
    }

    private void updateEndState() {
        if (gameState.isGameOver()) {
            return;
        }
        if (GameState.hasInsufficientMaterial(board.getBoard())) {
            gameState.declareInsufficientMaterialDraw();
            return;
        }
        if (hasAnyLegalMove()) {
            return;
        }
        if (MoveRule.isKingInCheck(board.getBoard(), whiteTurn)) {
            gameState.declareCheckmate(whiteTurn ? GameState.Player.BLACK : GameState.Player.WHITE);
        } else {
            gameState.declareStalemate();
        }
    }

    private GameState.Player currentPlayer() {
        return whiteTurn ? GameState.Player.WHITE : GameState.Player.BLACK;
    }

    private GameState.Player opposite(GameState.Player player) {
        return player == GameState.Player.WHITE ? GameState.Player.BLACK : GameState.Player.WHITE;
    }

    private String moveNotation(int piece, int fromRow, int fromCol, int toRow, int toCol, boolean capture) {
        if (isCastlingMove(fromRow, fromCol, toRow, toCol)) {
            return toCol == 6 ? "O-O" : "O-O-O";
        }
        String destination = String.valueOf((char) ('a' + toCol)) + (8 - toRow);
        if (Math.abs(piece) == 1) {
            return capture ? String.valueOf((char) ('a' + fromCol)) + "x" + destination : destination;
        }
        return pieceLetter(Math.abs(piece)) + (capture ? "x" : "") + destination;
    }

    private String pieceLetter(int pieceType) {
        switch (pieceType) {
            case 2: return "N";
            case 3: return "B";
            case 4: return "R";
            case 5: return "K";
            case 6: return "Q";
            default: return "";
        }
    }

    private boolean isPromotionPiece(int pieceType) {
        return pieceType == 2 || pieceType == 3 || pieceType == 4 || pieceType == 6;
    }

    private void restore(Snapshot snapshot) {
        board.setBoard(copyBoard(snapshot.boardData));
        whiteTurn = snapshot.whiteTurn;
        whiteKingMoved = snapshot.whiteKingMoved;
        blackKingMoved = snapshot.blackKingMoved;
        whiteLeftRookMoved = snapshot.whiteLeftRookMoved;
        whiteRightRookMoved = snapshot.whiteRightRookMoved;
        blackLeftRookMoved = snapshot.blackLeftRookMoved;
        blackRightRookMoved = snapshot.blackRightRookMoved;
        enPassantPawnRow = snapshot.enPassantPawnRow;
        enPassantPawnCol = snapshot.enPassantPawnCol;
        gameState.restore(snapshot.gameStateSnapshot);
    }

    private static int[][] copyBoard(int[][] source) {
        int[][] copy = new int[source.length][];
        for (int row = 0; row < source.length; row++) {
            copy[row] = source[row].clone();
        }
        return copy;
    }

    private boolean hasAnyLegalMove() {
        for (int row = 0; row < Board.SIZE_X; row++) {
            for (int col = 0; col < Board.SIZE_Y; col++) {
                if (!getLegalMoves(row, col).isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isInside(int row, int col) {
        return row >= 0 && row < Board.SIZE_X && col >= 0 && col < Board.SIZE_Y;
    }

    private String positionKey() {
        StringBuilder key = new StringBuilder(80);
        for (int[] row : board.getBoard()) {
            for (int piece : row) {
                key.append(piece).append(',');
            }
        }
        key.append(whiteTurn ? 'w' : 'b')
                .append(whiteKingMoved).append(blackKingMoved)
                .append(whiteLeftRookMoved).append(whiteRightRookMoved)
                .append(blackLeftRookMoved).append(blackRightRookMoved)
                .append(enPassantPawnRow).append(',').append(enPassantPawnCol);
        return key.toString();
    }
}
