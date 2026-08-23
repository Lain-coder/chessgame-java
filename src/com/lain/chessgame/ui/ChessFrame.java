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
import javax.imageio.ImageIO;
import java.awt.BorderLayout;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChessFrame extends JFrame {
    private static final int PIECE_ICON_INSET = 8;
    private static final String PIECE_IMAGE_PATH = "pieceImagePath";
    private final GameController game = new GameController();
    private final Map<String, ImageIcon> pieceIconCache = new HashMap<>();
    private final Map<String, BufferedImage> transparentPieceCache = new HashMap<>();
    private final ChessBoardPanel chessPanel = new ChessBoardPanel();
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
                button.setOpaque(false);
                button.setContentAreaFilled(false);
                button.setFocusPainted(false);
                button.setBorderPainted(false);
                button.setBorder(BorderFactory.createEmptyBorder());
                final int currentRow = row;
                final int currentCol = col;
                button.addActionListener(event -> handleSquareClick(currentRow, currentCol));

                String imagePath = getImagePath(data[row][col]);
                if (imagePath != null) {
                    button.putClientProperty(PIECE_IMAGE_PATH, imagePath);
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

    /**
     * 棋子原图中的浅灰棋盘格并不是真透明背景。加载时只清除与图片边缘连通的
     * 浅色中性像素，因而棋子轮廓内部的白色区域会被保留。随后按主体边界裁切，
     * 并根据当前棋格大小分级缩小，避免把 1254px 原图一次压成小图造成锯齿。
     */
    private ImageIcon loadPieceIcon(String imagePath, int iconSize) {
        String cacheKey = imagePath + "@" + iconSize;
        ImageIcon cached = pieceIconCache.get(cacheKey);
        if (cached != null) return cached;

        try {
            BufferedImage transparent = transparentPieceCache.get(imagePath);
            if (transparent == null) {
                BufferedImage source = ImageIO.read(getClass().getResource(imagePath));
                if (source == null) throw new IllegalArgumentException("无法读取棋子图片: " + imagePath);
                transparent = removeConnectedBackground(source);
                transparentPieceCache.put(imagePath, transparent);
            }
            ImageIcon icon = new ImageIcon(scaleToIcon(transparent, iconSize));
            pieceIconCache.put(cacheKey, icon);
            return icon;
        } catch (Exception exception) {
            throw new IllegalStateException("加载棋子图片失败: " + imagePath, exception);
        }
    }

    private static BufferedImage removeConnectedBackground(BufferedImage source) {
        int width = source.getWidth();
        int height = source.getHeight();
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D copy = result.createGraphics();
        copy.drawImage(source, 0, 0, null);
        copy.dispose();

        boolean[][] visited = new boolean[height][width];
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        for (int x = 0; x < width; x++) {
            enqueueBackground(result, x, 0, visited, queue);
            enqueueBackground(result, x, height - 1, visited, queue);
        }
        for (int y = 1; y < height - 1; y++) {
            enqueueBackground(result, 0, y, visited, queue);
            enqueueBackground(result, width - 1, y, visited, queue);
        }

        int[] dx = {1, -1, 0, 0};
        int[] dy = {0, 0, 1, -1};
        while (!queue.isEmpty()) {
            int position = queue.removeFirst();
            int x = position % width;
            int y = position / width;
            result.setRGB(x, y, result.getRGB(x, y) & 0x00FFFFFF);
            for (int direction = 0; direction < 4; direction++) {
                int nextX = x + dx[direction];
                int nextY = y + dy[direction];
                if (nextX >= 0 && nextX < width && nextY >= 0 && nextY < height) {
                    enqueueBackground(result, nextX, nextY, visited, queue);
                }
            }
        }
        return result;
    }

    private static void enqueueBackground(BufferedImage image, int x, int y,
                                          boolean[][] visited, ArrayDeque<Integer> queue) {
        if (visited[y][x]) return;
        visited[y][x] = true;
        if (isBakedTransparencyGrid(image.getRGB(x, y))) {
            queue.addLast(y * image.getWidth() + x);
        }
    }

    private static boolean isBakedTransparencyGrid(int argb) {
        int red = (argb >>> 16) & 0xFF;
        int green = (argb >>> 8) & 0xFF;
        int blue = argb & 0xFF;
        int brightest = Math.max(red, Math.max(green, blue));
        int darkest = Math.min(red, Math.min(green, blue));
        return darkest >= 225 && brightest - darkest <= 14;
    }

    private static BufferedImage scaleToIcon(BufferedImage source, int iconSize) {
        int minX = source.getWidth();
        int minY = source.getHeight();
        int maxX = -1;
        int maxY = -1;
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                if (((source.getRGB(x, y) >>> 24) & 0xFF) > 16) {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        if (maxX < minX || maxY < minY) return new BufferedImage(iconSize, iconSize, BufferedImage.TYPE_INT_ARGB);

        int sourceWidth = maxX - minX + 1;
        int sourceHeight = maxY - minY + 1;
        int padding = 2;
        double scale = Math.min((double) (iconSize - padding * 2) / sourceWidth,
                (double) (iconSize - padding * 2) / sourceHeight);
        int targetWidth = Math.max(1, (int) Math.round(sourceWidth * scale));
        int targetHeight = Math.max(1, (int) Math.round(sourceHeight * scale));
        int targetX = (iconSize - targetWidth) / 2;
        int targetY = (iconSize - targetHeight) / 2;

        BufferedImage cropped = new BufferedImage(sourceWidth, sourceHeight, BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics2D cropGraphics = cropped.createGraphics();
        cropGraphics.setComposite(AlphaComposite.Src);
        cropGraphics.drawImage(source, 0, 0, sourceWidth, sourceHeight,
                minX, minY, maxX + 1, maxY + 1, null);
        cropGraphics.dispose();
        BufferedImage scaled = resizeProgressively(cropped, targetWidth, targetHeight);

        BufferedImage icon = new BufferedImage(iconSize, iconSize, BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics2D graphics = icon.createGraphics();
        graphics.setComposite(AlphaComposite.Src);
        graphics.drawImage(scaled, targetX, targetY, null);
        graphics.dispose();
        return icon;
    }

    private static BufferedImage resizeProgressively(BufferedImage source, int targetWidth, int targetHeight) {
        BufferedImage current = source;
        while (current.getWidth() != targetWidth || current.getHeight() != targetHeight) {
            int nextWidth = Math.max(targetWidth, current.getWidth() / 2);
            int nextHeight = Math.max(targetHeight, current.getHeight() / 2);
            BufferedImage next = new BufferedImage(nextWidth, nextHeight, BufferedImage.TYPE_INT_ARGB_PRE);
            Graphics2D graphics = next.createGraphics();
            graphics.setComposite(AlphaComposite.Src);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.drawImage(current, 0, 0, nextWidth, nextHeight, null);
            graphics.dispose();
            current = next;
        }
        return current;
    }

    private void decorateSquare(JButton button, int row, int col, int piece) {
        if (row == selectedRow && col == selectedCol) {
            button.setBorderPainted(true);
            button.setBorder(BorderFactory.createLineBorder(new Color(255, 196, 0), 4));
        } else if (isLegalDestination(row, col)) {
            button.setBorderPainted(true);
            button.setBorder(BorderFactory.createLineBorder(
                    piece == 0 ? new Color(57, 150, 75) : new Color(196, 55, 55), 4));
        } else {
            button.setBorderPainted(false);
            button.setBorder(BorderFactory.createEmptyBorder());
        }
    }

    /** 用裁去边框的猫主题色块拼成棋盘，并让棋子按钮透明叠在棋格上。 */
    private final class ChessBoardPanel extends JPanel {
        private static final double TEXTURE_CROP_INSET = 0.065;
        private final Image darkSquareImage = new ImageIcon(getClass().getResource("/images/squareB.png")).getImage();
        private final Image lightSquareImage = new ImageIcon(getClass().getResource("/images/squareW.png")).getImage();

        private ChessBoardPanel() {
            setLayout(null);
            setPreferredSize(new Dimension(650, 650));
            setBackground(new Color(35, 35, 35));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            int boardSize = Math.min(getWidth(), getHeight());
            int x = (getWidth() - boardSize) / 2;
            int y = (getHeight() - boardSize) / 2;
            Graphics2D graphics2D = (Graphics2D) graphics.create();
            graphics2D.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            paintSquareTextures(graphics2D, x, y, boardSize);
            graphics2D.dispose();
        }

        private void paintSquareTextures(Graphics2D graphics2D, int boardX, int boardY, int boardSize) {
            for (int row = 0; row < 8; row++) {
                for (int col = 0; col < 8; col++) {
                    Image texture = (row + col) % 2 == 0 ? lightSquareImage : darkSquareImage;
                    int left = boardX + col * boardSize / 8;
                    int right = boardX + (col + 1) * boardSize / 8;
                    int top = boardY + row * boardSize / 8;
                    int bottom = boardY + (row + 1) * boardSize / 8;

                    int sourceWidth = texture.getWidth(this);
                    int sourceHeight = texture.getHeight(this);
                    int sourceInsetX = (int) Math.round(sourceWidth * TEXTURE_CROP_INSET);
                    int sourceInsetY = (int) Math.round(sourceHeight * TEXTURE_CROP_INSET);
                    graphics2D.drawImage(texture,
                            left, top, right, bottom,
                            sourceInsetX, sourceInsetY,
                            sourceWidth - sourceInsetX, sourceHeight - sourceInsetY,
                            this);
                }
            }
        }

        @Override
        public void doLayout() {
            int boardSize = Math.min(getWidth(), getHeight());
            int boardX = (getWidth() - boardSize) / 2;
            int boardY = (getHeight() - boardSize) / 2;
            for (int index = 0; index < getComponentCount(); index++) {
                int row = index / 8;
                int col = index % 8;
                int left = boardX + col * boardSize / 8;
                int right = boardX + (col + 1) * boardSize / 8;
                int top = boardY + row * boardSize / 8;
                int bottom = boardY + (row + 1) * boardSize / 8;
                JButton button = (JButton) getComponent(index);
                int cellWidth = right - left;
                int cellHeight = bottom - top;
                button.setBounds(left, top, cellWidth, cellHeight);
                Object imagePath = button.getClientProperty(PIECE_IMAGE_PATH);
                if (imagePath instanceof String) {
                    int iconSize = Math.max(1, Math.min(cellWidth, cellHeight) - PIECE_ICON_INSET);
                    if (button.getIcon() == null || button.getIcon().getIconWidth() != iconSize) {
                        button.setIcon(loadPieceIcon((String) imagePath, iconSize));
                    }
                }
            }
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
