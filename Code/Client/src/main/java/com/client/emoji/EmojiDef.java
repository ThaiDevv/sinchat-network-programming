package com.client.emoji;

/**
 * Data class for a single emoji definition.
 */
public class EmojiDef {
    private final String code;       // English code: "smile"
    private final String label;      // Vietnamese label: "[cười]"
    private final String desc;       // Description: "Cười"
    private final String fileName;   // PNG filename without ext: "smile"
    private final int gifNum;        // GIF number: 100

    public EmojiDef(String code, String label, String desc, String fileName, int gifNum) {
        this.code = code;
        this.label = label;
        this.desc = desc;
        this.fileName = fileName;
        this.gifNum = gifNum;
    }

    public String getCode()     { return code; }
    public String getLabel()    { return label; }
    public String getDesc()     { return desc; }
    public String getFileName() { return fileName; }
    public int getGifNum()      { return gifNum; }

    @Override
    public String toString() {
        return label + " (" + desc + ")";
    }
}
