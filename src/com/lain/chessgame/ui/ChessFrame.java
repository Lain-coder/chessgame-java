package com.lain.chessgame.ui;

import com.lain.chessgame.game.GameController;
import com.lain.chessgame.game.gamerule.GameState;
import com.lain.chessgame.piece.pieceset.Bishop;
import com.lain.chessgame.piece.pieceset.King;
import com.lain.chessgame.piece.pieceset.Knight;
import com.lain.chessgame.piece.pieceset.Pawn;
import com.lain.chessgame.piece.pieceset.Queen;
import com.lain.chessgame.piece.pieceset.Rook;

import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Image;
import java.util.Collections;
import java.util.List;

public class ChessFrame extends JFrame {
    private static final Color LIGHT_SQUARE = new Color(240, 217, 181);
    private static final Color DARK_SQUARE = new Color(181, 136, 99);

    private final GameController game = new GameController();
    private final JPanel chessPanel = new JPanel(new GridLayout(8, 8));
    private final JLabel statusLabel = new JLabel();
    private final JTextArea notationArea = new JTextArea();
    private int selectedRow = -1;
    private int selectedCol = -1;
    private List<GameController.Square> legalMoves = Collections.emptyList();

    public ChessFrame() {
        setTitle("国际象棋");
        setSize(920, 670);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());
        add(chessPanel, BorderLayout.CENTER);
        add(createSidePanel(), BorderLayout.EAST);
        refreshView();
        setVisible(true);
    }

    private JPanel createSidePanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        panel.setPreferredSize(new Dimension(260, 0));
        statusLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
        panel.add(statusLabel, BorderLayout.NORTH);
        notationArea.setEditable(false);
        notationArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        panel.add(new JScrollPane(notationArea), BorderLayout.CENTER);

        JPanel actions = new JPanel(new GridLayout(3, 2, 6, 6));
        actions.add(actionButton("请求和棋", this::requestDraw));
        actions.add(actionButton("接受和棋", this::acceptDraw));
        actions.add(actionButton("拒绝和棋", this::declineDraw));
        actions.add(actionButton("请求悔棋", this::requestUndo));
        actions.add(actionButton("接受悔棋", this::acceptUndo));
        actions.add(actionButton("拒绝悔棋", this::declineUndo));
        panel.add(actions, BorderLayout.SOUTH);
        return panel;
    }

    private JButton actionButton(String text, Runnable action) {
        JButton button = new JButton(text);
        button.addActionListener(event -> action.run());
        return button;
    }

    private void refreshView() {
        chessPanel.removeAll();
        int[][] data = game.getBoard().getBoard();
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                JButton button = new JButton();
                button.setBackground((row + col) % 2 == 0 ? LIGHT_SQUARE : DARK_SQUARE);
                button.setFocusPainted(false);
                button.setBorderPainted(true);
                button.setBorder(BorderFactory.createEmptyBorder());
                final int currentRow = row;
                final int currentCol = col;
                button.addActionListener(event -> handleSquareClick(currentRow, currentCol));

                String imagePath = getImagePath(data[row][col]);
                if (imagePath != null) {
                    Image image = new ImageIcon(getClass().getResource(imagePath)).getImage()
                            .getScaledInstance(65, 65, Image.SCALE_SMOOTH);
                    button.setIcon(new ImageIcon(image));
                }
                decorateSquare(button, row, col, data[row][col]);
                chessPanel.add(button);
            }
        }
        updateStatus();
        updateNotation();
        chessPanel.revalidate();
        chessPanel.repaint();
    }

    private void decorateSquare(JButton button, int row, int col, int piece) {
        if (row == selectedRow && col == selectedCol) {
            button.setBorder(BorderFactory.createLineBorder(new Color(255, 196, 0), 4));
        } else if (isLegalDestination(row, col)) {
            button.setBorder(BorderFactory.createLineBorder(
                    piece == 0 ? new Color(57, 150, 75) : new Color(196, 55, 55), 4));
        }
    }

    private boolean isLegalDestination(int row, int col) {
        for (GameController.Square move : legalMoves) {
            if (move.getRow() == row && move.getCol() == col) return true;
        }
        return false;
    }

    private void handleSquareClick(int row, int col) {
        if (game.getGameState().isGameOver()) return;
        int clickedPiece = game.getBoard().getPiece(row, col);
        if (selectedRow == -1 || game.isCurrentPlayerPiece(clickedPiece)) {
            selectPiece(clickedPiece, row, col);
        } else if (isLegalDestination(row, col)) {
            game.move(selectedRow, selectedCol, row, col, choosePromotionPiece(selectedRow, selectedCol, row));
            clearSelection();
        } else {
            clearSelection();
        }
        refreshView();
    }

    private void selectPiece(int piece, int row, int col) {
        if (game.isCurrentPlayerPiece(piece)) {
            selectedRow = row;
            selectedCol = col;
            legalMoves = game.getLegalMoves(row, col);
        }
    }

    private void clearSelection() {
        selectedRow = -1;
        selectedCol = -1;
        legalMoves = Collections.emptyList();
    }

    private int choosePromotionPiece(int fromRow, int fromCol, int toRow) {
        if (!game.canPromote(fromRow, fromCol, toRow)) return Queen.WHITE_QUEEN;
        Object[] choices = {"后", "车", "象", "马"};
        Object answer = JOptionPane.showInputDialog(this, "请选择兵升变后的棋子", "兵升变",
                JOptionPane.QUESTION_MESSAGE, null, choices, choices[0]);
        if ("车".equals(answer)) return Rook.WHITE_ROOK;
        if ("象".equals(answer)) return Bishop.WHITE_BISHOP;
        if ("马".equals(answer)) return Knight.WHITE_KNIGHT;
        return Queen.WHITE_QUEEN;
    }

    private void requestDraw() { showActionResult(game.requestDraw(), "已提出和棋请求。", "当前不能提出和棋请求。"); }
    private void acceptDraw() { showActionResult(game.acceptDraw(), "双方同意和棋。", "当前没有可接受的和棋请求。"); }
    private void declineDraw() { game.declineDraw(); refreshView(); }
    private void requestUndo() { showActionResult(game.requestUndo(), "已请求悔棋，请对手决定是否接受。", "当前不能请求悔棋。"); }
    private void acceptUndo() { showActionResult(game.acceptUndo(), "已悔棋，恢复到上一步之前。", "当前没有可接受的悔棋请求。"); }
    private void declineUndo() { game.declineUndo(); refreshView(); }

    private void showActionResult(boolean success, String successMessage, String failureMessage) {
        JOptionPane.showMessageDialog(this, success ? successMessage : failureMessage);
        clearSelection();
        refreshView();
    }

    private void updateStatus() {
        GameState state = game.getGameState();
        if (state.isGameOver()) statusLabel.setText(resultText(state.getResult()));
        else if (state.getDrawOfferer() != null) statusLabel.setText(
                (state.getDrawOfferer() == GameState.Player.WHITE ? "白方" : "黑方") + "请求和棋");
        else statusLabel.setText((game.isWhiteTurn() ? "白方" : "黑方") + "走棋"
                + (game.isCurrentPlayerInCheck() ? "（被将军）" : ""));
    }

    private String resultText(GameState.Result result) {
        switch (result) {
            case WHITE_WINS_BY_CHECKMATE: return "将死：白方胜";
            case BLACK_WINS_BY_CHECKMATE: return "将死：黑方胜";
            case DRAW_BY_AGREEMENT: return "和棋：双方同意";
            case DRAW_BY_FIFTY_MOVE_RULE: return "和棋：五十步规则";
            case DRAW_BY_STALEMATE: return "和棋：逼和";
            case DRAW_BY_INSUFFICIENT_MATERIAL: return "和棋：子力不足";
            case DRAW_BY_THREEFOLD_REPETITION: return "和棋：三次重复局面";
            default: return "对局结束";
        }
    }

    private void updateNotation() {
        StringBuilder text = new StringBuilder();
        List<String> moves = game.getMoveHistory();
        for (int index = 0; index < moves.size(); index++) {
            if (index % 2 == 0) text.append(index / 2 + 1).append(". ");
            text.append(moves.get(index)).append(index % 2 == 0 ? "  " : "\n");
        }
        notationArea.setText(text.toString());
        notationArea.setCaretPosition(notationArea.getDocument().getLength());
    }

    private String getImagePath(int piece) {
        if (piece == Pawn.WHITE_PAWN) return "/images/white_pawn.png";
        if (piece == Pawn.BLACK_PAWN) return "/images/black_pawn.png";
        if (piece == Knight.WHITE_KNIGHT) return "/images/white_knight.png";
        if (piece == Knight.BLACK_KNIGHT) return "/images/black_knight.png";
        if (piece == Bishop.WHITE_BISHOP) return "/images/white_bishop.png";
        if (piece == Bishop.BLACK_BISHOP) return "/images/black_bishop.png";
        if (piece == Rook.WHITE_ROOK) return "/images/white_rook.png";
        if (piece == Rook.BLACK_ROOK) return "/images/black_rook.png";
        if (piece == Queen.WHITE_QUEEN) return "/images/white_queen.png";
        if (piece == Queen.BLACK_QUEEN) return "/images/black_queen.png";
        if (piece == King.WHITE_KING) return "/images/white_king.png";
        if (piece == King.BLACK_KING) return "/images/black_king.png";
        return null;
    }
}
