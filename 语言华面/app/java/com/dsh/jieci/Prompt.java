package com.dsh.jieci;

public final class Prompt {

    public static final String SYSTEM = "你是一个严谨的词源与语义解释引擎。你只输出 JSON，不聊天，不说教，不输出任何 JSON 以外的文字。";

    private Prompt() {}

    public static String build(String word, String lang) {
        String langName = Langs.name(lang);
        StringBuilder b = new StringBuilder();
        b.append("用户选择的语言是").append(langName).append("。请用下面的方法解释这个")
                .append(langName).append("单词或短语，解释文字用简体中文。\n\n");
        b.append("方法（必须全部遵守）：\n");
        b.append("1. 只拆该语言中肉眼可见、能独立识别的词根、前缀、后缀、词尾变化。不能拆的整体就整体记：decomposable 设为 false，components 设为空数组，并在 note 里写一句整体记忆的提示。\n");
        b.append("2. 词缀、词尾只写功能标签（例如“名词后缀”“否定”“方向：向内”“表示状态”），绝对不要给它们编造具体画面。\n");
        b.append("3. 先找到这个词最底层的原始物理动作（推、拉、解开、覆盖、穿过、握住、落下、分开、包围等），必须包含身体、空间、力、触感、边界中的至少一项。\n");
        b.append("4. 根源画面：由原始动作展开成一个可感知的场景，但只聚焦一个核心点，不要写成故事，不要依赖专有名词。形容词的画面要“模糊但聚焦一个点”。\n");
        b.append("5. 核心意思分两层：initial_meaning（初意，靠近原始动作）和 abstract_meanings（抽象义，按常见程度排序，多义词要列全）。每个抽象义用 from 说明它从原始动作的哪一部分长出来。\n");
        b.append("6. examples：至少给两个").append(langName).append("例句，并配中文翻译；每个例子还要有 tag，标明它展示的是哪个义项，格式如“初意，分发”“抽象义①，双方交换条件”，tag 不加句号。\n");
        b.append("7. short_image：比根源画面更短的一句话，保留核心动作。\n");
        b.append("8. anchor：全篇只给一个速记锚点，是一个动作或画面，不超过 40 个字。\n");
        b.append("9. 禁止与其他词对比，禁止出现“与……相比”“不同于”“区别”“对比”这类表述。\n");
        b.append("10. 解释全部用简体中文，例句保持").append(langName).append("原句。\n");
        b.append("11. 如果是短语，先判断它是“固定搭配”“功能结构”还是“普通组合”，写入 phrase_type；固定搭配优先按整体语义解释。\n");
        b.append("12. 如果输入是日语，注意汉字与假名的可见块；如果是俄语、西班牙语、法语、德语，注意词根、前缀、后缀和词尾变化。\n");
        b.append("13. phonetic_uk / phonetic_us：当语言是英语时，给英式（DJ）和美式（KK）音标，带斜杠；其他语言填空字符串。\n");
        b.append("14. reading：非英语时给读音（日语假名、俄语/西班牙语/法语/德语转写、文言文拼音），英语填空字符串。\n");
        b.append("15. evolution_history：只有语言是文言文时填写，写清字形本义、假借或引申的支路、时代、现代残留；其他语言填空字符串。\n");
        b.append("16. original_bold / root_bold：分别从 original_action / root_image 的原文字符串里摘出最核心的一小句，必须逐字一致；没有明显中心句就填空字符串。\n\n");
        b.append("只输出 JSON，不要 markdown 代码块，不要任何解释性文字。JSON 结构如下：\n");
        b.append("{\n");
        b.append("  \"word\": \"原样返回用户输入\",\n");
        b.append("  \"type\": \"word 或 phrase\",\n");
        b.append("  \"phrase_type\": \"短语时填：固定搭配/功能结构/普通组合；单词填空字符串\",\n");
        b.append("  \"decomposable\": true,\n");
        b.append("  \"components\": [{\"part\": \"可见块\", \"function\": \"这一块本身的意思或功能\"}],\n");
        b.append("  \"note\": \"不可拆时写整体记忆的提示；可拆时填空字符串\",\n");
        b.append("  \"original_action\": \"最底层的物理动作\",\n");
        b.append("  \"root_image\": \"聚焦一个核心点的可感知画面\",\n");
        b.append("  \"initial_meaning\": \"初意\",\n");
        b.append("  \"abstract_meanings\": [{\"meaning\": \"抽象义\", \"from\": \"从原始动作的哪一部分长出来\"}],\n");
        b.append("  \"examples\": [{\"sentence\": \"该语言的原句\", \"zh\": \"中文翻译\", \"tag\": \"初意，简短说明\"}],\n");
        b.append("  \"short_image\": \"更短的核心画面\",\n");
        b.append("  \"anchor\": \"一个动作或画面，不超过 40 个字\",\n");
        b.append("  \"phonetic_uk\": \"/.../（仅英语）\",\n");
        b.append("  \"phonetic_us\": \"/.../（仅英语）\",\n");
        b.append("  \"reading\": \"非英语的读音\",\n");
        b.append("  \"evolution_history\": \"仅文言文的演化史\",\n");
        b.append("  \"original_bold\": \"original_action 中的核心一小句，没有就空\",\n");
        b.append("  \"root_bold\": \"root_image 中的核心一小句，没有就空\"\n");
        b.append("}\n\n");
        b.append("用户输入：").append(word).append("\n");
        return b.toString();
    }
}
