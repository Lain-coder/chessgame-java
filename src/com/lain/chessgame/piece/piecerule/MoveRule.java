package com.lain.chessgame.piece.piecerule;

/**
 * 国际象棋基础走子规则。
 * 棋盘中正数代表白方，负数代表黑方；绝对值代表棋子类型。
 */
public final class MoveRule {

    private static final int PAWN = 1;
    private static final int KNIGHT = 2;
    private static final int BISHOP = 3;
    private static final int ROOK = 4;
    private static final int KING = 5;
    private static final int QUEEN = 6;

    private MoveRule() {
    }

    /**
     * 判断一次基础走子是否合法。该方法会同时判断吃子：目标格只能为空，或为敌方棋子。
     * 不包含王车易位、吃过路兵，以及“走完后己方王不能被将军”的高级规则。
     */
    public static boolean isLegalMove(int[][] board, int fromRow, int fromCol, int toRow, int toCol) {
        if (!isInside(board, fromRow, fromCol) || !isInside(board, toRow, toCol)
                || (fromRow == toRow && fromCol == toCol)) {
            return false;
        }

        int piece = board[fromRow][fromCol];
        int target = board[toRow][toCol];
        if (piece == 0 || Math.abs(target) == KING || isSameColor(piece, target)) {
            return false;
        }

        int rowDistance = toRow - fromRow;
        int colDistance = toCol - fromCol;
        switch (Math.abs(piece)) {
            case PAWN:
                return isLegalPawnMove(board, piece, fromRow, fromCol, toRow, toCol);
            case KNIGHT:
                return (Math.abs(rowDistance) == 2 && Math.abs(colDistance) == 1)
                        || (Math.abs(rowDistance) == 1 && Math.abs(colDistance) == 2);
            case BISHOP:
                return Math.abs(rowDistance) == Math.abs(colDistance)
                        && isPathClear(board, fromRow, fromCol, toRow, toCol);
            case ROOK:
                return (rowDistance == 0 || colDistance == 0)
                        && isPathClear(board, fromRow, fromCol, toRow, toCol);
            case QUEEN:
                return (rowDistance == 0 || colDistance == 0
                        || Math.abs(rowDistance) == Math.abs(colDistance))
                        && isPathClear(board, fromRow, fromCol, toRow, toCol);
            case KING:
                return Math.max(Math.abs(rowDistance), Math.abs(colDistance)) == 1;
            default:
                return false;
        }
    }

    public static boolean isSameColor(int firstPiece, int secondPiece) {
        return firstPiece != 0 && secondPiece != 0
                && Integer.signum(firstPiece) == Integer.signum(secondPiece);
    }

    /** 判断指定一方是否正在攻击目标格；用于判断将军和王能否落子。 */
    public static boolean isSquareAttacked(int[][] board, int targetRow, int targetCol, boolean attackedByWhite) {
        for (int row = 0; row < board.length; row++) {
            for (int col = 0; col < board[row].length; col++) {
                int piece = board[row][col];
                if (piece == 0 || (piece > 0) != attackedByWhite) {
                    continue;
                }
                if (attacksSquare(board, row, col, targetRow, targetCol)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean isKingInCheck(int[][] board, boolean whiteKing) {
        for (int row = 0; row < board.length; row++) {
            for (int col = 0; col < board[row].length; col++) {
                if (board[row][col] == (whiteKing ? KING : -KING)) {
                    return isSquareAttacked(board, row, col, !whiteKing);
                }
            }
        }
        // 局面中没有王属于非法局面，不能继续走子。
        return true;
    }

    /** 在普通走子后，己方王不能处于被将军状态。 */
    public static boolean leavesKingInCheck(int[][] board, int fromRow, int fromCol, int toRow, int toCol) {
        int piece = board[fromRow][fromCol];
        int captured = board[toRow][toCol];
        board[toRow][toCol] = piece;
        board[fromRow][fromCol] = 0;
        boolean inCheck = isKingInCheck(board, piece > 0);
        board[fromRow][fromCol] = piece;
        board[toRow][toCol] = captured;
        return inCheck;
    }

    private static boolean attacksSquare(int[][] board, int fromRow, int fromCol, int toRow, int toCol) {
        int piece = board[fromRow][fromCol];
        int rowDistance = toRow - fromRow;
        int colDistance = toCol - fromCol;
        switch (Math.abs(piece)) {
            case PAWN:
                int direction = piece > 0 ? -1 : 1;
                return rowDistance == direction && Math.abs(colDistance) == 1;
            case KNIGHT:
                return (Math.abs(rowDistance) == 2 && Math.abs(colDistance) == 1)
                        || (Math.abs(rowDistance) == 1 && Math.abs(colDistance) == 2);
            case BISHOP:
                return Math.abs(rowDistance) == Math.abs(colDistance)
                        && isPathClear(board, fromRow, fromCol, toRow, toCol);
            case ROOK:
                return (rowDistance == 0 || colDistance == 0)
                        && isPathClear(board, fromRow, fromCol, toRow, toCol);
            case QUEEN:
                return (rowDistance == 0 || colDistance == 0
                        || Math.abs(rowDistance) == Math.abs(colDistance))
                        && isPathClear(board, fromRow, fromCol, toRow, toCol);
            case KING:
                return Math.max(Math.abs(rowDistance), Math.abs(colDistance)) == 1;
            default:
                return false;
        }
    }

    private static boolean isLegalPawnMove(int[][] board, int piece, int fromRow, int fromCol,
                                           int toRow, int toCol) {
        int direction = piece > 0 ? -1 : 1;
        int startingRow = piece > 0 ? 6 : 1;
        int rowDistance = toRow - fromRow;
        int colDistance = toCol - fromCol;
        int target = board[toRow][toCol];

        // 兵向前移动时，目标格必须为空。
        if (colDistance == 0 && target == 0) {
            if (rowDistance == direction) {
                return true;
            }
            return fromRow == startingRow && rowDistance == 2 * direction
                    && board[fromRow + direction][fromCol] == 0;
        }

        // 兵只能斜向吃敌方棋子。
        return rowDistance == direction && Math.abs(colDistance) == 1
                && target != 0 && !isSameColor(piece, target);
    }

    private static boolean isPathClear(int[][] board, int fromRow, int fromCol, int toRow, int toCol) {
        int rowStep = Integer.compare(toRow, fromRow);
        int colStep = Integer.compare(toCol, fromCol);
        int row = fromRow + rowStep;
        int col = fromCol + colStep;

        while (row != toRow || col != toCol) {
            if (board[row][col] != 0) {
                return false;
            }
            row += rowStep;
            col += colStep;
        }
        return true;
    }

    private static boolean isInside(int[][] board, int row, int col) {
        return board != null && row >= 0 && row < board.length
                && board[row] != null && col >= 0 && col < board[row].length;
    }
}
