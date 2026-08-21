package com.lain.chessgame.board;

import com.lain.chessgame.piece.pieceset.*;

public class Board {
    //构建棋盘
    public static final int SIZE_X = 8;
    public static final int SIZE_Y = 8;

    private int[][] board;


    public Board() {
        board = new int[SIZE_X][SIZE_Y];

        initializeBoard();
    }

    //在这里摆上棋子
    public void initializeBoard() {
    //白兵放在第7行，黑兵放在第2行
        for (int col = 0; col < SIZE_Y; col++) {
            board[1][col] = Pawn.BLACK_PAWN;
            board[6][col] = Pawn.WHITE_PAWN;
        }
    //黑马在第一行第二列和第一行第七列，白马在第八行第二列与第八行第七列
        {
            board[0][1] = Knight.BLACK_KNIGHT ;
            board[0][6] = Knight.BLACK_KNIGHT;

            board[7][1] = Knight.WHITE_KNIGHT ;
            board[7][6] = Knight.WHITE_KNIGHT ;
        }

        // 主教：第 1、8 行的第 3、6 列
        board[0][2] = Bishop.BLACK_BISHOP;
        board[0][5] = Bishop.BLACK_BISHOP;

        board[7][2] = Bishop.WHITE_BISHOP;
        board[7][5] = Bishop.WHITE_BISHOP;

// 车：第 1、8 行的第 1、8 列
        board[0][0] = Rook.BLACK_ROOK;
        board[0][7] = Rook.BLACK_ROOK;

        board[7][0] = Rook.WHITE_ROOK;
        board[7][7] = Rook.WHITE_ROOK;

// 后：第 1、8 行的第 4 列
        board[0][3] = Queen.BLACK_QUEEN;
        board[7][3] = Queen.WHITE_QUEEN;

// 王：第 1、8 行的第 5 列
        board[0][4] = King.BLACK_KING;
        board[7][4] = King.WHITE_KING;

    }
    //获得棋盘棋盘
    public int[][] getBoard() {
        return board;
    }

    public int getPiece(int row, int col) {
        return board[row][col];
    }

    /** 移动棋子；调用前应先用 MoveRule 校验走法。 */
    public void movePiece(int fromRow, int fromCol, int toRow, int toCol) {
        board[toRow][toCol] = board[fromRow][fromCol];
        board[fromRow][fromCol] = 0;
    }
    //打印棋盘
    public void setBoard(int[][] board) {
        this.board = board;

    }
    public void printBoard() {
        for (int row = 0; row < SIZE_Y; row++) {
            for (int col = 0; col < SIZE_X; col++) {
                System.out.printf("%3d", board[row][col]);
            }
            System.out.println();
        }
    }

    }
