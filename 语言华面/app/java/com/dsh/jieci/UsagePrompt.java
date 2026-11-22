package com.dsh.jieci;

public final class UsagePrompt {

    public static final String SYSTEM = "你是一个像母语者一样解释语言用法的引擎。你只输出 JSON，不聊天，不列教学公式，不输出任何 JSON 以外的文字。";

    private UsagePrompt() {}

    public static String build(String topic, String lang) {
        String langName = Langs.name(lang);
        StringBuilder b = new StringBuilder();
        b.append("用户选择的语言是").append(langName).append("。请解释这个").append(langName)
                .append("语言用法，解释文字用简体中文。\n\n");
        b.append("要求：\n");
        b.append("1. native_frame：用一段话写“母语者心里在做什么”。比如把现实挪开一层、给动作设一个参照点、把两件事叠在一起。不要写“主句用……从句用……”这种句型公式。\n");
        b.append("2. types：列出这个用法的常见类型 3–8 个。每个 type 给 name 和 use，use 只写一句话“母语者用它来做什么”，不写形式公式。\n");
        b.append("3. related_words：列出 3–6 个相关关键词。每个给 word 和 gloss，gloss 用一句物理画面或功能解释它，不要求完整词条。\n");
        b.append("4. scenes：给 3–7 个独立的典型场景。每个场景包含 scene（场景名）、utterance（原句）、why（说话者为什么选这个形式）、effect（听者会怎么理解）。场景不能是同一句话换词。\n");
        b.append("5. anchor：全篇只给一个速记锚点，一句话。\n");
        b.append("6. closing：用一句话收束，像母语者说完后的感觉。\n");
        b.append("7. 禁止与其他用法对比，禁止出现“与……相比”“不同于”“区别”“对比”这类表述。\n");
        b.append("8. 禁止中国课堂式口诀，禁止把公式当解释主体；形式标签最多放在括号里辅助。\n\n");
        b.append("只输出 JSON，不要 markdown 代码块，不要任何解释性文字。JSON 结构如下：\n");
        b.append("{\n");
        b.append("  \"topic\": \"原样返回用户输入\",\n");
        b.append("  \"language\": \"").append(langName).append("\",\n");
        b.append("  \"native_frame\": \"母语者心里在做什么\",\n");
        b.append("  \"types\": [{\"name\": \"类型名\", \"use\": \"一句话用途\"}],\n");
        b.append("  \"related_words\": [{\"word\": \"if\", \"gloss\": \"一句物理画面或功能\"}],\n");
        b.append("  \"scenes\": [{\"scene\": \"场景名\", \"utterance\": \"原句\", \"why\": \"为什么选这个形式\", \"effect\": \"听者会怎么理解\"}],\n");
        b.append("  \"anchor\": \"一句速记锚点\",\n");
        b.append("  \"closing\": \"一句话收束\"\n");
        b.append("}\n\n");
        b.append("用户输入：").append(topic).append("\n");
        return b.toString();
    }
}
