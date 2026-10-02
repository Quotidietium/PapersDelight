package dev.tako.papersdelight.util;

import dev.tako.papersdelight.bench.Bench;
import dev.tako.papersdelight.bench.Bench.Result;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Random;
import java.util.regex.Pattern;

/**
 * TextUtil.parse 文本热路径基准（R4）。
 *
 * 行为面（两种 jar 均可运行，跨标签直接对比）：
 *  - mm-tag,static-pool：同一批典型 GUI/lang 字符串反复解析——生产 GUI 刷新的真实形态；
 *    R4（解析结果缓存）侧为缓存命中，R3 及以前侧为每次完整解析。
 *  - mm-tag,dynamic-key：计数器后缀的永不重复字符串——R4 侧为 miss+回填（有界代价），
 *    对照侧为完整解析。
 *  - plain-legacy：无 MiniMessage 标签的 legacy 路径。
 *  - parseList 8 行：典型 lore 列表。
 *
 * 语义面（自检）：
 *  - 确定性：相同输入两次解析 equals 相同；
 *  - 路由正确：带标签输入按 MiniMessage 解析（plain 序列化文本内容）；
 *  - R4 侧（反射探测 hasMiniMessageTag 存在时）：零分配扫描器与原正则
 *    <[a-zA-Z#][^>]*> 在构造语料 + 确定性随机模糊下逐例等价；缓存命中返回共享实例。
 *    基线侧无该方法则跳过（旧 jar 兼容）。
 */
public final class TextParseBench {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final Pattern MINI_MESSAGE_DETECT = Pattern.compile("<[a-zA-Z#][^>]*>");

    private static final String[] STATIC_POOL = {
            "<!i><white>42%", "<!i><gray>耗时: <white>300 秒", "<!i><gray>经验: <white>1.0",
            "<!i><yellow>第 1 / 8 页", "<!i><green>下一页", "<!i><gray>已经是第一页了",
            "<!i><white><lang:jei.farmersdelight.cooking>", "<!i><aqua>容器",
            "<!i><gray>已加载配方: <white>64", "<!i><gray>点击查看第 <yellow>2 <gray>页",
            "<red><bold>警告</bold></red>", "<#ff5500>渐变示例", "<gray>─── <white>信息 <gray>───",
            "<!i><dark_gray>空", "<!i><blue>精炼: <white>II", "<green>✔ 可自动填充",
            "<!i><white>营养值 <yellow>67%", "<!i><gray>剩馀 3 份", "<!i><red>需要热源",
            "<yellow>食谱浏览", "<!i><gray>Shift 点击取餐", "<!i><white>汤品", "<!i><white>主食",
            "<!i><gray>烹饪锅 · <white>展示", "<!i><gold>招牌菜", "<!i><gray>容器: <white>碗",
            "<!i><gray>耐久: <white>64/64", "<!i><blue>浸泡中...", "<!i><aqua>清水 ×2",
            "<!i><gray>漏斗已连接", "<white> </white>", "<!i><gray>第 1 层", "<!i><gray>第 2 层",
            "<!i><yellow>★ 收藏", "<!i><gray>点击翻页", "<!i><red>已取消", "<!i><green>已完成",
            "<!i><gray>时间 <white>00:15", "<!i><gray>来源: <white>厨锅", "<!i><white>全部取出",
            "<!i><gray>自动填充", "<!i><yellow>金锅", "<!i><gray>铁锅", "<!i><gray>订单 · <white>#12",
            "<!i><blue>瓶装蜂蜜", "<!i><green>热可可", "<!i><red>番茄浓汤", "<!i><yellow>烤马铃薯",
            "<!i><gray>蔬菜汤 ×3", "<!i><white>面食", "<!i><gray>甜点", "<!i><gray>饮料",
            "<!i><aqua>清水", "<!i><gray>牛奶", "<!i><gray>苹果汁", "<!i><gold>南瓜派",
            "<!i><gray>煎蛋卷 ×2", "<!i><green>烤鱼", "<!i><red>辣汤", "<!i><yellow>海鲜饭",
            "<!i><gray>烹饪时间 <white>x1.0", "<!i><gray>火候: <red>旺火", "<!i><white>餐盘",
            "<!i><gray>点击查看配方",
    };

    private static final List<String> LORE_8 = List.of(
            "<!i><gray>耗时: <white>300 秒",
            "<!i><gray>经验: <white>1.0",
            "<!i><gray>条件: <white>厨锅下方需要热源",
            "<!i><gray>容器: <white>碗",
            "<!i><gray>─── <white>原料 <gray>───",
            "<!i><white>番茄 ×1",
            "<!i><white>面条 ×1",
            "<!i><gray>Shift 点击自动摆放");

    public static void run(List<Result> results) {
        selfCheck();

        results.add(Bench.measure("text.parse[mm-tag,static-pool]", (bh, ops) -> {
            for (int i = 0; i < ops; i++) {
                bh.consume(TextUtil.parse(null, STATIC_POOL[i & 63]));
            }
        }));

        results.add(Bench.measure("text.parse[mm-tag,dynamic-key]", (bh, ops) -> {
            for (int i = 0; i < ops; i++) {
                bh.consume(TextUtil.parse(null, "<!i><gray>动态 <white>#" + (i & 0xFFFF)));
            }
        }));

        String[] plains = {"&7普通说明文本", "plain lore line", "&a绿色&f带码", "短文本", "x", "&e标题文本行"};
        results.add(Bench.measure("text.parse[plain-legacy]", (bh, ops) -> {
            for (int i = 0; i < ops; i++) {
                bh.consume(TextUtil.parse(null, plains[i % plains.length]));
            }
        }));

        results.add(Bench.measure("text.parseList[8-lines,static]", (bh, ops) -> {
            for (int i = 0; i < ops; i++) {
                bh.consume(TextUtil.parseList(null, LORE_8));
            }
        }));
    }

    private static void selfCheck() {
        // 空输入
        Bench.check(TextUtil.parse(null, "") != null, "empty parses to non-null");

        // 确定性（两种 jar 都必须成立）
        Component a = TextUtil.parse(null, "<red>hi <bold>world");
        Component b = TextUtil.parse(null, "<red>hi <bold>world");
        Bench.check(a.equals(b), "parse deterministic");
        Bench.check(PLAIN.serialize(a).equals("hi world"), "mm tag stripped in plain text");

        // legacy 路径：& 码文本
        Component legacy = TextUtil.parse(null, "&7灰色文本");
        Bench.check(PLAIN.serialize(legacy).equals("灰色文本"), "legacy path plain text");

        // 带 % 的字符串在 player==null 下也必须确定性（无 PAPI 通道）
        Component pct = TextUtil.parse(null, "<!i><gray>耗时: <white>%time% 秒");
        Bench.check(PLAIN.serialize(pct).equals("耗时: %time% 秒"), "percent preserved without player");

        // R4 侧专属校验：扫描器等价 + 缓存共享实例（旧 jar 上方法不存在则跳过）
        Method scanner;
        try {
            scanner = TextUtil.class.getDeclaredMethod("hasMiniMessageTag", String.class);
        } catch (NoSuchMethodException e) {
            return;
        }
        scanner.setAccessible(true);
        String[] corpus = {
                "", "<", "a", "<a", "<a>", "<A>", "<#>", "<#ff0000>", "<!i>", "<!i><red>x",
                "<red>", "<b c>", "<b<c>", "a<b", "<<a>", "<a<b>", "x<>y", "<é>", "<_>", "< >",
                "1<2>", "<<>>", "<a>>", "<<a>", "<Z", "Z<", "<aXy>", "文本<red>标签", "<red>文本",
                "<#>", "<#a>", "a<a>b<c>d", "<a><", "><a>", "<a<>", "<>>",
        };
        for (String s : corpus) {
            Bench.check(scan(scanner, s) == MINI_MESSAGE_DETECT.matcher(s).find(),
                    "scanner equivalence: " + describe(s));
        }
        // 确定性随机模糊（固定种子，可复现）
        Random random = new Random(0x5EED_0000L);
        char[] alphabet = {'<', '>', 'a', 'Z', '#', '!', ' ', 'x', '&'};
        for (int i = 0; i < 10000; i++) {
            int len = random.nextInt(13);
            StringBuilder sb = new StringBuilder(len);
            for (int j = 0; j < len; j++) sb.append(alphabet[random.nextInt(alphabet.length)]);
            String s = sb.toString();
            Bench.check(scan(scanner, s) == MINI_MESSAGE_DETECT.matcher(s).find(),
                    "scanner fuzz equivalence: " + describe(s));
        }

        // 缓存命中返回共享实例（resolved==text 时）
        Component c1 = TextUtil.parse(null, "<!i><blue>缓存身份校验");
        Component c2 = TextUtil.parse(null, "<!i><blue>缓存身份校验");
        Bench.check(c1 == c2, "cached parse returns shared instance");
        // 且语义等价
        Bench.check(c1.equals(c2), "cached instance semantically equal");
    }

    private static boolean scan(Method scanner, String s) {
        try {
            return (Boolean) scanner.invoke(null, s);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("scanner invoke failed", e);
        }
    }

    private static String describe(String s) {
        return "'" + s.replace("\n", "\\n") + "'";
    }

    private TextParseBench() {}
}
