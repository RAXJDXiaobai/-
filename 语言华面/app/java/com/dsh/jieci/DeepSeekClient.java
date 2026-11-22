package com.dsh.jieci;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class DeepSeekClient {

    public static final class Result {
        public final Explanation explanation;
        public final String json;

        Result(Explanation explanation, String json) {
            this.explanation = explanation;
            this.json = json;
        }
    }

    public static final class UsageResult {
        public final UsageExplanation explanation;
        public final String json;

        UsageResult(UsageExplanation explanation, String json) {
            this.explanation = explanation;
            this.json = json;
        }
    }

    public static final class ApiException extends Exception {
        public final int code;

        ApiException(int code, String message) {
            super(message);
            this.code = code;
        }
    }

    private DeepSeekClient() {}

    public static Result explain(String word, String lang, String apiKey, String baseUrl, String model) throws ApiException {
        String content = request(Prompt.SYSTEM, Prompt.build(word, lang), apiKey, baseUrl, model, 2200);
        try {
            JSONObject obj = new JSONObject(extractJson(content));
            Explanation ex = Explanation.fromJson(obj);
            if (!ex.valid()) {
                throw new ApiException(0, "生成的内容不完整，请再试一次");
            }
            if (ex.word == null || ex.word.trim().isEmpty()) ex.word = word;
            return new Result(ex, obj.toString());
        } catch (JSONException e) {
            throw new ApiException(-2, "生成结果格式不对，请再试一次");
        }
    }

    public static UsageResult explainUsage(String topic, String lang, String apiKey, String baseUrl, String model) throws ApiException {
        String content = request(UsagePrompt.SYSTEM, UsagePrompt.build(topic, lang), apiKey, baseUrl, model, 2800);
        try {
            JSONObject obj = new JSONObject(extractJson(content));
            UsageExplanation ex = UsageExplanation.fromJson(obj);
            if (!ex.valid()) {
                throw new ApiException(0, "生成的语言用法不完整，请再试一次");
            }
            if (ex.topic.isEmpty()) ex.topic = topic;
            return new UsageResult(ex, obj.toString());
        } catch (JSONException e) {
            throw new ApiException(-2, "生成结果格式不对，请再试一次");
        }
    }

    private static String request(String system, String prompt, String apiKey, String baseUrl,
                                  String model, int maxTokens) throws ApiException {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(endpoint(baseUrl));
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(20000);
            conn.setReadTimeout(90000);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            conn.setDoOutput(true);

            JSONObject body = new JSONObject();
            body.put("model", model);
            body.put("temperature", 0.8);
            body.put("max_tokens", maxTokens);
            body.put("stream", false);
            body.put("response_format", new JSONObject().put("type", "json_object"));

            JSONArray messages = new JSONArray();
            messages.put(new JSONObject().put("role", "system").put("content", system));
            messages.put(new JSONObject().put("role", "user").put("content", prompt));
            body.put("messages", messages);

            byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
            OutputStream os = conn.getOutputStream();
            os.write(payload);
            os.flush();
            os.close();

            int code = conn.getResponseCode();
            InputStream in = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
            String text = read(in);

            if (code != 200) {
                throw new ApiException(code, friendly(code, text));
            }

            JSONObject resp = new JSONObject(text);
            JSONArray choices = resp.optJSONArray("choices");
            if (choices == null || choices.length() == 0) {
                throw new ApiException(0, "服务没有返回内容，请重试");
            }
            JSONObject message = choices.getJSONObject(0).optJSONObject("message");
            return message == null ? "" : message.optString("content", "");
        } catch (ApiException e) {
            throw e;
        } catch (IOException e) {
            throw new ApiException(-1, "网络连接失败，请检查手机网络后重试");
        } catch (JSONException e) {
            throw new ApiException(-2, "生成结果格式不对，请再试一次");
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static String endpoint(String base) {
        String b = base == null ? "" : base.trim();
        if (b.isEmpty()) b = Prefs.DEFAULT_BASE;
        while (b.endsWith("/")) b = b.substring(0, b.length() - 1);
        if (b.endsWith("/chat/completions")) return b;
        return b + "/chat/completions";
    }

    private static String read(InputStream in) throws IOException {
        if (in == null) return "";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        in.close();
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    private static String extractJson(String content) throws JSONException {
        String s = content == null ? "" : content.trim();
        if (s.startsWith("```")) {
            int start = s.indexOf('\n');
            if (start > 0) s = s.substring(start + 1);
            if (s.endsWith("```")) s = s.substring(0, s.length() - 3);
            s = s.trim();
        }
        int a = s.indexOf('{');
        int b = s.lastIndexOf('}');
        if (a < 0 || b <= a) throw new JSONException("no json object");
        return s.substring(a, b + 1);
    }

    private static String friendly(int code, String body) {
        if (code == 401) return "钥匙不对或已失效，请到设置里检查";
        if (code == 402) return "账户余额不足，请到 DeepSeek 开放平台充值";
        if (code == 429) return "请求太频繁了，歇一会儿再试";
        if (code == 400) return "这次请求被拒绝了，换一种写法再试";
        String detail = "";
        try {
            JSONObject o = new JSONObject(body);
            JSONObject err = o.optJSONObject("error");
            if (err != null) detail = err.optString("message", "");
        } catch (Exception ignored) {
        }
        if (!detail.isEmpty()) return "服务返回：" + detail;
        return "服务出错了（" + code + "），请稍后重试";
    }
}
