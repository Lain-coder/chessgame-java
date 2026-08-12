package com.lain.chessgame.ui;

import com.lain.chessgame.board.Board;
import com.lain.chessgame.piece.pieceset.Bishop;
import com.lain.chessgame.piece.pieceset.King;
import com.lain.chessgame.piece.pieceset.Knight;
import com.lain.chessgame.piece.pieceset.Pawn;
import com.lain.chessgame.piece.pieceset.Queen;
import com.lain.chessgame.piece.pieceset.Rook;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import java.awt.Color;
import java.awt.GridLayout;
import java.awt.Image;

public class ChessFrame extends JFrame {

    private Board board;

    public ChessFrame() {
        board = new Board();
        board.initializeBoard();

        setTitle("国际象棋");
        setSize(640, 640);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        createChessBoard();

        setVisible(true);
    }

    private void createChessBoard() {
        JPanel chessPanel = new JPanel();
        chessPanel.setLayout(new GridLayout(8, 8));

        int[][] data = board.getBoard();

        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                JButton button = new JButton();

                // 设置黑白相间的棋盘格
                if ((row + col) % 2 == 0) {
                    button.setBackground(new Color(240, 217, 181));
                } else {
                    button.setBackground(new Color(181, 136, 99));
                }

                // 去掉按钮默认边框，棋盘更好看
                button.setBorderPainted(false);
                button.setFocusPainted(false);

                int piece = data[row][col];
                String imagePath = getImagePath(piece);

                // 有棋子才显示对应图片
                if (imagePath != null) {
                    ImageIcon oldIcon = new ImageIcon(
                            getClass().getResource(imagePath)
                    );

                    Image image = oldIcon.getImage().getScaledInstance(
                            65, 65, Image.SCALE_SMOOTH
                    );

                    button.setIcon(new ImageIcon(image));
                }

                chessPanel.add(button);
            }
        }

        add(chessPanel);
    }

    // 根据棋盘中的数字，得到对应的图片路径
    private String getImagePath(int piece) {
        if (piece == Pawn.WHITE_PAWN) {
            return "/images/white_pawn.png";
        }
        if (piece == Pawn.BLACK_PAWN) {
            return "/images/black_pawn.png";
        }

        if (piece == Knight.WHITE_KNIGHT) {
            return "/images/white_knight.png";
        }
        if (piece == Knight.BLACK_KNIGHT) {
            return "/images/black_knight.png";
        }

        if (piece == Bishop.WHITE_BISHOP) {
            return "/images/white_bishop.png";
        }
        if (piece == Bishop.BLACK_BISHOP) {
            return "/images/black_bishop.png";
        }

        if (piece == Rook.WHITE_ROOK) {
            return "/images/white_rook.png";
        }
        if (piece == Rook.BLACK_ROOK) {
            return "/images/black_rook.png";
        }

        if (piece == Queen.WHITE_QUEEN) {
            return "/images/white_queen.png";
        }
        if (piece == Queen.BLACK_QUEEN) {
            return "/images/black_queen.png";
        }

        if (piece == King.WHITE_KING) {
            return "/images/white_king.png";
        }
        if (piece == King.BLACK_KING) {
            return "/images/black_king.png";
        }

        return null;
    }
}