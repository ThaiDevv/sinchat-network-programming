package com.client.emoji;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Manages all emoji definitions, caching, and rendering.
 * Singleton — loaded once at startup from emoji_list.json.
 *
 * Rendering rules (WeChat-style):
 * - 1 emoji alone          → large animated GIF (120x120)
 * - 2+ emojis only         → small static PNG inline (28x28)
 * - text + emoji(s)        → small static PNG inline (28x28)
 * - text only              → plain Label
 */
public class EmojiManager {

    // ── Singleton ──────────────────────────────────────────────────
    private static EmojiManager instance;

    public static EmojiManager getInstance() {
        if (instance == null) instance = new EmojiManager();
        return instance;
    }

    // ── Constants ──────────────────────────────────────────────────
    private static final int SIZE_ANIMATED = 120;
    private static final int SIZE_STATIC   = 28;
    private static final String EMOJI_JSON = "/emojis/emoji_list.json";
    private static final String GIF_PATH   = "/emojis/animated/%d.gif";
    private static final String PNG_PATH   = "/emojis/static/%s.png";

    // ── Data ───────────────────────────────────────────────────────
    private final List<EmojiDef> emojiList = new ArrayList<>();
    private final Map<String, EmojiDef> defByLabel = new LinkedHashMap<>(); // "[cười]" → EmojiDef
    private final Map<String, Image> animCache   = new HashMap<>();        // code → GIF
    private final Map<String, Image> staticCache = new HashMap<>();        // code → PNG
    private Pattern emojiPattern;
    private boolean loaded = false;

    // ── Constructor ────────────────────────────────────────────────
    private EmojiManager() {
        loadDefinitions();
    }

    // ── Load from JSON ─────────────────────────────────────────────
    private void loadDefinitions() {
        try (InputStream is = getClass().getResourceAsStream(EMOJI_JSON)) {
            if (is == null) {
                System.err.println("[EmojiManager] ⚠ emoji_list.json not found in resources, using hardcoded fallback");
                loadFallbackDefinitions();
                buildPattern();
                loaded = true;
                return;
            }
            Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8);
            com.google.gson.JsonArray arr = com.google.gson.JsonParser.parseReader(reader).getAsJsonArray();
            for (int i = 0; i < arr.size(); i++) {
                com.google.gson.JsonObject obj = arr.get(i).getAsJsonObject();
                String code     = obj.get("code").getAsString();
                String label    = obj.get("label").getAsString();
                String desc     = obj.get("desc").getAsString();
                String fileName = obj.get("fileName").getAsString();
                int gifNum      = obj.get("gifNum").getAsInt();
                EmojiDef def = new EmojiDef(code, label, desc, fileName, gifNum);
                emojiList.add(def);
                defByLabel.put(label, def);
            }
        } catch (Exception e) {
            System.err.println("[EmojiManager] ⚠ Failed to load emoji_list.json: " + e.getMessage());
            loadFallbackDefinitions();
        }
        // Sort by label length descending for regex priority
        emojiList.sort((a, b) -> b.getLabel().length() - a.getLabel().length());
        buildPattern();
        loaded = true;
    }

    private void loadFallbackDefinitions() {
        emojiList.clear();
        defByLabel.clear();
        addFallback("cuoi", "[cười]",     "Cười",         "smile",      100);
        addFallback("gian", "[giận]",     "Tức giận",     "angry",      111);
        addFallback("buon", "[buồn]",     "Buồn",         "frown",      115);
        addFallback("tim",  "[tim]",      "Yêu thích",    "heart",      166);
        addFallback("ngac", "[ngạc]",     "Ngạc nhiên",   "surprise",   114);
        addFallback("khoc", "[khóc]",     "Khóc",         "cry",        109);
        addFallback("cute", "[cute]",     "Dễ thương",    "shy",        106);
        addFallback("ok",   "[ok]",       "Đồng ý",       "ok",         189);
        addFallback("cuoiTo","[cười to]", "Cười lớn",     "laugh",      128);
        addFallback("nhayMat","[nháy]",   "Nháy mắt",     "sly",        151);
        addFallback("hon",  "[hôn]",      "Hôn",          "kiss",       152);
        addFallback("ngu",  "[ngủ]",      "Buồn ngủ",     "sleep",      108);
        addFallback("tuc",  "[tức]",      "Tức giận",     "wrath",      153);
        addFallback("hoang","[hoảng]",    "Hoảng sợ",     "panic",      126);
        addFallback("lanh", "[lạnh]",     "Lạnh",         "sweat",      127);
        addFallback("dam",  "[đấm]",      "Đấm",          "fist",       185);
        addFallback("like", "[like]",     "Thích",        "thumbs_up",  179);
        addFallback("dislike","[dislike]","Không thích",  "thumbs_down",180);
        addFallback("vay",  "[vẫy]",      "Vẫy tay",      "wave",       139);
        addFallback("thom", "[thơm]",     "Thơm",         "blowkiss",   191);
        addFallback("hoa",  "[hoa]",      "Hoa hồng",     "rose",       163);
        addFallback("mat",  "[mát]",      "Mát mẻ",       "cool_guy",   104);
        addFallback("xauho","[xấu hổ]",   "Xấu hổ",       "blush",      117);
        addFallback("ngon", "[ngon]",     "Ngon",         "drool",      102);
        addFallback("beer", "[bia]",      "Bia",          "beer",       157);
        addFallback("coffee","[cà phê]",  "Cà phê",       "coffee",     160);
        addFallback("cake", "[bánh]",     "Bánh",         "cake",       168);
        addFallback("bomb", "[bom]",      "Bom",          "bomb",       170);
        addFallback("moon", "[trăng]",    "Trăng",        "moon",       175);
        addFallback("sun",  "[nắng]",     "Mặt trời",     "sun",        176);
        addFallback("gift", "[quà]",      "Quà",          "gift",       177);
        addFallback("hug",  "[ôm]",       "Ôm",           "hug",        178);
        addFallback("peace","[hoà bình]", "Hoà bình",     "peace",      182);
    }

    private void addFallback(String code, String label, String desc, String fileName, int gifNum) {
        EmojiDef def = new EmojiDef(code, label, desc, fileName, gifNum);
        emojiList.add(def);
        defByLabel.put(label, def);
    }

    private void buildPattern() {
        StringBuilder sb = new StringBuilder();
        for (EmojiDef def : emojiList) {
            if (sb.length() > 0) sb.append("|");
            sb.append(Pattern.quote(def.getLabel()));
        }
        emojiPattern = Pattern.compile(sb.toString());
    }

    // ── Public API ─────────────────────────────────────────────────

    public List<EmojiDef> getEmojiList() { return emojiList; }

    /** Expose the compiled emoji pattern for external rendering (e.g. input overlay). */
    public Pattern getEmojiPattern() { return emojiPattern; }

    /** Look up an EmojiDef by its label, e.g. "[rên rỉ]". */
    public EmojiDef getEmojiDefByLabel(String label) { return defByLabel.get(label); }

    /** Load (or retrieve from cache) a static PNG emoji image by fileName. */
    public Image getStaticEmojiImage(String fileName) { return loadStatic(fileName); }

    /** Check if a message consists entirely of emoji labels */
    public boolean isPureEmoji(String text) {
        if (text == null || text.isEmpty()) return false;
        String stripped = emojiPattern.matcher(text).replaceAll("").trim();
        return stripped.isEmpty();
    }

    /** Count emoji labels in the text */
    public int countEmojis(String text) {
        if (text == null) return 0;
        int count = 0;
        Matcher m = emojiPattern.matcher(text);
        while (m.find()) count++;
        return count;
    }

    // ── Image loading ──────────────────────────────────────────────

    private Image loadAnimated(String code, int gifNum) {
        return animCache.computeIfAbsent(code, c -> {
            String path = String.format(GIF_PATH, gifNum);
            Image img = loadImage(path, SIZE_ANIMATED);
            return img;
        });
    }

    private Image loadStatic(String fileName) {
        return staticCache.computeIfAbsent(fileName, fn -> {
            String path = String.format(PNG_PATH, fn);
            Image img = loadImage(path, SIZE_STATIC);
            return img;
        });
    }

    private Image loadImage(String resourcePath, int size) {
        try {
            InputStream is = getClass().getResourceAsStream(resourcePath);
            if (is == null) {
                System.err.println("[EmojiManager] ⚠ Resource not found: " + resourcePath);
                return null;
            }
            Image img = new Image(is, size, size, true, true);
            is.close();
            return img;
        } catch (Exception e) {
            System.err.println("[EmojiManager] ⚠ Failed to load: " + resourcePath + " — " + e.getMessage());
            return null;
        }
    }

    // ── Rendering ──────────────────────────────────────────────────

    /**
     * Parse a message text and return a JavaFX Node for display.
     * - Pure text → Label
     * - Single emoji → ImageView (animated GIF, 120x120)
     * - Text + emoji(s) or multiple emojis → TextFlow with inline images (28x28)
     */
    public Node renderMessage(String text) {
        if (text == null || text.isEmpty()) {
            return new Label("");
        }

        int emojiCount = countEmojis(text);
        if (emojiCount == 0) {
            // Pure text — return a simple Label
            Label label = new Label(text);
            label.setWrapText(true);
            label.setMaxWidth(360);
            return label;
        }

        boolean isPureEmoji = isPureEmoji(text);
        if (isPureEmoji && emojiCount == 1) {
            // Single emoji → large animated GIF
            Matcher m = emojiPattern.matcher(text);
            if (m.find()) {
                String label = m.group();
                EmojiDef def = defByLabel.get(label);
                if (def != null) {
                    Image gif = loadAnimated(def.getCode(), def.getGifNum());
                    if (gif != null) {
                        ImageView iv = new ImageView(gif);
                        iv.setFitWidth(SIZE_ANIMATED);
                        iv.setFitHeight(SIZE_ANIMATED);
                        iv.setPreserveRatio(true);
                        return iv;
                    }
                }
            }
            // Fallback: show label text
            return new Label(text);
        }

        // Multiple emojis or text+emoji → TextFlow with inline images
        return buildTextFlow(text);
    }

    /**
     * Build a TextFlow mixing Text nodes and ImageView nodes.
     */
    private TextFlow buildTextFlow(String text) {
        TextFlow flow = new TextFlow();
        flow.setLineSpacing(4);

        Matcher m = emojiPattern.matcher(text);
        int lastEnd = 0;

        while (m.find()) {
            // Add text before this emoji
            if (m.start() > lastEnd) {
                String before = text.substring(lastEnd, m.start());
                Text t = new Text(before);
                t.setStyle("-fx-fill: #ffffff; -fx-font-size: 15px;");
                flow.getChildren().add(t);
            }

            // Add emoji image
            String label = m.group();
            EmojiDef def = defByLabel.get(label);
            if (def != null) {
                Image png = loadStatic(def.getFileName());
                if (png != null) {
                    ImageView iv = new ImageView(png);
                    iv.setFitWidth(SIZE_STATIC);
                    iv.setFitHeight(SIZE_STATIC);
                    iv.setPreserveRatio(true);
                    flow.getChildren().add(iv);
                } else {
                    // Fallback: show label as text
                    Text fallback = new Text(label);
                    fallback.setStyle("-fx-fill: #ffd166; -fx-font-size: 15px;");
                    flow.getChildren().add(fallback);
                }
            } else {
                Text unknown = new Text(label);
                unknown.setStyle("-fx-fill: #aaaaaa; -fx-font-size: 15px;");
                flow.getChildren().add(unknown);
            }

            lastEnd = m.end();
        }

        // Add remaining text after last emoji
        if (lastEnd < text.length()) {
            String after = text.substring(lastEnd);
            Text t = new Text(after);
            t.setStyle("-fx-fill: #ffffff; -fx-font-size: 15px;");
            flow.getChildren().add(t);
        }

        flow.setMaxWidth(360);
        return flow;
    }

    /**
     * Render a message for preview contexts (reply bar, quote box).
     * Always uses small inline images (20x20) and dimmed text styling,
     * unlike {@link #renderMessage} which uses large animated GIFs for solo emojis.
     */
    public Node renderMessagePreview(String text) {
        if (text == null || text.isEmpty()) {
            return new Label("");
        }

        int emojiCount = countEmojis(text);
        if (emojiCount == 0) {
            // Pure text — small label for preview
            Label label = new Label(text);
            label.setWrapText(true);
            label.setMaxWidth(300);
            label.setStyle("-fx-font-size: 12px; -fx-text-fill: #cccccc;");
            return label;
        }

        // Always use TextFlow style with small inline images for preview
        return buildTextFlowPreview(text);
    }

    /**
     * Build a TextFlow for preview contexts with small images and dimmed text.
     */
    private TextFlow buildTextFlowPreview(String text) {
        TextFlow flow = new TextFlow();
        flow.setLineSpacing(2);
        flow.setMaxWidth(300);

        Matcher m = emojiPattern.matcher(text);
        int lastEnd = 0;

        while (m.find()) {
            // Add text before this emoji
            if (m.start() > lastEnd) {
                String before = text.substring(lastEnd, m.start());
                Text t = new Text(before);
                t.setStyle("-fx-fill: #cccccc; -fx-font-size: 12px;");
                flow.getChildren().add(t);
            }

            // Add emoji image (small, 20x20)
            String label = m.group();
            EmojiDef def = defByLabel.get(label);
            if (def != null) {
                Image png = loadStatic(def.getFileName());
                if (png != null) {
                    ImageView iv = new ImageView(png);
                    iv.setFitWidth(20);
                    iv.setFitHeight(20);
                    iv.setPreserveRatio(true);
                    flow.getChildren().add(iv);
                } else {
                    // Fallback: show label as dimmed text
                    Text fallback = new Text(label);
                    fallback.setStyle("-fx-fill: #ffd166; -fx-font-size: 12px;");
                    flow.getChildren().add(fallback);
                }
            } else {
                Text unknown = new Text(label);
                unknown.setStyle("-fx-fill: #888888; -fx-font-size: 12px;");
                flow.getChildren().add(unknown);
            }

            lastEnd = m.end();
        }

        // Add remaining text after last emoji
        if (lastEnd < text.length()) {
            String after = text.substring(lastEnd);
            Text t = new Text(after);
            t.setStyle("-fx-fill: #cccccc; -fx-font-size: 12px;");
            flow.getChildren().add(t);
        }

        return flow;
    }

    /**
     * Create an emoji picker grid (VBox of HBox rows).
     * Each emoji is a clickable ImageView showing the static PNG.
     */
    public VBox createEmojiPicker(java.util.function.Consumer<String> onEmojiSelected) {
        VBox grid = new VBox(4);
        grid.setStyle("-fx-background-color: #1a1a1a; -fx-padding: 10px; -fx-background-radius: 12px;");

        final int COLS = 8;
        final int CELL_SIZE = 36;
        HBox row = new HBox(2);
        row.setAlignment(Pos.CENTER_LEFT);
        int count = 0;

        for (EmojiDef def : emojiList) {
            StackPane cell = new StackPane();
            cell.setMinSize(CELL_SIZE, CELL_SIZE);
            cell.setPrefSize(CELL_SIZE, CELL_SIZE);
            cell.setMaxSize(CELL_SIZE, CELL_SIZE);
            cell.setStyle("-fx-background-radius: 6px; -fx-cursor: hand;");

            // Load static PNG for the picker
            Image png = loadStatic(def.getFileName());
            if (png != null) {
                ImageView iv = new ImageView(png);
                iv.setFitWidth(28);
                iv.setFitHeight(28);
                iv.setPreserveRatio(true);
                cell.getChildren().add(iv);
            } else {
                // Fallback: show label text if image not found
                Label fallback = new Label(def.getLabel());
                fallback.setStyle("-fx-font-size: 11px; -fx-text-fill: #aaaaaa;");
                cell.getChildren().add(fallback);
            }

            // Tooltip with description
            Tooltip tip = new Tooltip(def.getDesc());
            tip.setStyle("-fx-font-size: 13px;");
            Tooltip.install(cell, tip);

            // Hover effect
            cell.setOnMouseEntered(e ->
                cell.setStyle("-fx-background-color: #333333; -fx-background-radius: 6px; -fx-cursor: hand;")
            );
            cell.setOnMouseExited(e ->
                cell.setStyle("-fx-background-radius: 6px; -fx-cursor: hand;")
            );

            // Click to insert label
            cell.setOnMouseClicked(e -> {
                if (onEmojiSelected != null) {
                    onEmojiSelected.accept(def.getLabel());
                }
            });

            row.getChildren().add(cell);
            count++;

            if (count % COLS == 0) {
                grid.getChildren().add(row);
                row = new HBox(2);
                row.setAlignment(Pos.CENTER_LEFT);
            }
        }

        // Add last incomplete row
        if (count % COLS != 0) {
            grid.getChildren().add(row);
        }

        return grid;
    }
}
