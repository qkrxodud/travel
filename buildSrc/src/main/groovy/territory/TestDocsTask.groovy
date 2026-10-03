package territory

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

import javax.xml.parsers.DocumentBuilderFactory

/**
 * 테스트 소스를 읽어 선언 순서 그대로(클래스 → @Nested → 테스트, @DisplayName 사용) 모듈별 규칙 문서와 목차를 만든다.
 * JUnit 실행 순서(@Nested 역순·메서드 해시 순)와 Gradle HTML 보고서의 FQCN 정렬에 기대지 않으려는 것이다.
 * 최근 테스트 결과 XML(build/test-results/**)이 있으면 각 줄에 ✓(통과)·✗(실패)·–(건너뜀)·○(결과 없음)을 붙인다.
 * 파서는 간단한 토크나이저다: 주석·문자열을 건너뛰며 중괄호 깊이로 클래스 중첩을 따라간다.
 */
abstract class TestDocsTask extends DefaultTask {

    /** 모듈 이름 → 테스트 소스 루트(src/test/java) 절대 경로 */
    @Input abstract MapProperty<String, String> getSourceRoots()

    /** 모듈 이름 → 테스트 결과 루트(build/test-results) 절대 경로 */
    @Input abstract MapProperty<String, String> getResultRoots()

    @InputFiles @PathSensitive(PathSensitivity.RELATIVE)
    abstract ConfigurableFileCollection getSources()

    @InputFiles @PathSensitive(PathSensitivity.RELATIVE)
    abstract ConfigurableFileCollection getResults()

    @OutputDirectory abstract DirectoryProperty getOutputDir()

    static final Set<String> TEST_ANNOTATIONS = ['Test', 'ParameterizedTest', 'RepeatedTest', 'TestFactory', 'TestTemplate'] as Set

    @TaskAction
    void generate() {
        File out = outputDir.get().asFile
        out.mkdirs()
        out.listFiles()?.findAll { it.name.endsWith('.md') }?.each { it.delete() }
        List<Map> summaries = []
        sourceRoots.get().each { String module, String root ->
            File rootDir = new File(root)
            if (!rootDir.isDirectory()) return
            List<Node> classes = []
            rootDir.eachFileRecurse { File f ->
                if (f.isFile() && f.name.endsWith('.java')) {
                    classes.addAll(new SourceParser(f.getText('UTF-8'), rootDir.toPath().relativize(f.toPath()).toString()).parse())
                }
            }
            classes = classes.findAll { it.testCount() > 0 }.sort { it.path }
            if (classes.isEmpty()) return
            Results results = Results.load(new File(resultRoots.get()[module] ?: ''))
            classes.each { results.apply(it) }
            new File(out, "${module}.md").setText(render(module, classes, results), 'UTF-8')
            summaries << [module: module, classes: classes, results: results]
        }
        new File(out, 'index.md').setText(renderIndex(summaries), 'UTF-8')
        logger.lifecycle("테스트 문서: ${new File(out, 'index.md')}")
    }

    // ---------------- 렌더링 ----------------

    static final String LEGEND = '✓ 통과 · ✗ 실패 · – 건너뜀 · ○ 결과 없음'

    static String render(String module, List<Node> classes, Results results) {
        StringBuilder sb = new StringBuilder()
        sb << "# ${module}\n\n"
        sb << "> 테스트 소스의 선언 순서(클래스 → 상황 → 결과)대로 적었다. "
        sb << (results.found ? "결과: ${results.latest} 기준 — ${LEGEND}\n\n" : "테스트 결과가 없어 통과 표시는 생략했다(`./gradlew test` 뒤 다시 실행).\n\n")
        sb << "[목차로](index.md)\n\n"
        classes.each { Node c ->
            sb << "## ${c.title}\n\n"
            sb << "<sub>${c.path}</sub>\n\n"
            c.children.each { renderNode(sb, it, 0, results.found) }
            sb << '\n'
        }
        sb.toString()
    }

    static void renderNode(StringBuilder sb, Node n, int level, boolean marks) {
        if (n.testCount() == 0) return
        String indent = '  ' * level
        if (n.test) {
            String mark = marks ? n.mark() + ' ' : ''
            String extra = n.cases.size() > 1 ? " (${n.cases.size()}가지)" : ''
            sb << "${indent}- ${mark}${n.title}${extra}\n"
            if (n.cases.size() > 1 || (n.cases.size() == 1 && n.cases[0].name != n.title)) {
                n.cases.each { sb << "${indent}  - ${marks ? markOf([it]) + ' ' : ''}${it.name}\n" }
            }
        } else {
            sb << "${indent}- **${n.title}**\n"
            n.children.each { renderNode(sb, it, level + 1, marks) }
        }
    }

    static String renderIndex(List<Map> summaries) {
        StringBuilder sb = new StringBuilder()
        sb << "# 규칙 문서 목차\n\n"
        sb << "> 모듈마다 테스트 소스 선언 순서대로 뽑은 규칙 문서다. ${LEGEND}\n\n"
        sb << "| 모듈 | 개념(클래스) | 규칙(테스트) | ✓ | ✗ | – | ○ |\n|---|---:|---:|---:|---:|---:|---:|\n"
        summaries.each { Map s ->
            List<Node> tests = s.classes.collectMany { it.tests() }
            Map counts = tests.countBy { it.mark() }
            sb << "| [${s.module}](${s.module}.md) | ${s.classes.size()} | ${tests.size()} | ${counts['✓'] ?: 0} | ${counts['✗'] ?: 0} | ${counts['–'] ?: 0} | ${counts['○'] ?: 0} |\n"
        }
        sb << '\n'
        summaries.each { Map s ->
            sb << "## [${s.module}](${s.module}.md)\n\n"
            s.classes.each { Node c -> sb << "- ${c.title} (${c.testCount()})\n" }
            sb << '\n'
        }
        sb.toString()
    }

    static String markOf(List<Case> cases) {
        if (cases.isEmpty()) return '○'
        if (cases.any { it.status == 'failed' }) return '✗'
        if (cases.every { it.status == 'skipped' }) return '–'
        return '✓'
    }

    // ---------------- 모델 ----------------

    static class Case { String name; String status }

    static class Node {
        String title
        String fqcn          // 클래스일 때 결과 XML 의 classname (a.b.Outer$Inner)
        String path          // 최상위 클래스의 소스 상대 경로
        boolean test
        String method
        String nameTemplate  // @ParameterizedTest·@RepeatedTest 의 name
        String displayName   // @DisplayName 원문(없으면 null)
        List<Node> children = []
        List<Case> cases = []

        int testCount() { test ? 1 : children.sum(0) { it.testCount() } as int }
        List<Node> tests() { test ? [this] : children.collectMany { it.tests() } }
        String mark() { markOf(cases) }
    }

    // ---------------- 결과 XML ----------------

    static class Results {
        boolean found
        String latest = ''
        Map<String, List<Case>> byClass = [:]

        static Results load(File root) {
            Results r = new Results()
            if (!root.isDirectory()) return r
            def factory = DocumentBuilderFactory.newInstance()
            root.eachFileRecurse { File f ->
                if (!(f.isFile() && f.name.startsWith('TEST-') && f.name.endsWith('.xml'))) return
                def doc = factory.newDocumentBuilder().parse(f)
                def suite = doc.documentElement
                String ts = suite.getAttribute('timestamp')
                if (ts > r.latest) r.latest = ts
                def nodes = suite.getElementsByTagName('testcase')
                for (int i = 0; i < nodes.length; i++) {
                    def tc = nodes.item(i)
                    String status = 'passed'
                    def kids = tc.childNodes
                    for (int k = 0; k < kids.length; k++) {
                        String tag = kids.item(k).nodeName
                        if (tag == 'failure' || tag == 'error') status = 'failed'
                        else if (tag == 'skipped' && status != 'failed') status = 'skipped'
                    }
                    r.byClass.computeIfAbsent(tc.getAttribute('classname')) { [] } << new Case(name: tc.getAttribute('name'), status: status)
                    r.found = true
                }
            }
            r
        }

        /** 클래스별로 결과를 소스의 테스트에 붙인다. 이름이 정확히 같은 것부터, 그다음 name 틀(정규식)로 맞춘다. */
        void apply(Node cls) {
            List<Case> pool = new ArrayList<>(byClass[cls.fqcn] ?: [])
            List<Node> direct = cls.children.findAll { it.test }
            direct.findAll { it.nameTemplate == null }.each { Node t ->
                String expected = t.displayName ?: "${t.method}()"
                Case c = pool.find { it.name == expected }
                if (c) { t.cases << c; pool.remove(c) }
            }
            direct.findAll { it.nameTemplate != null }.each { Node t ->
                def regex = templateRegex(t)
                List<Case> hit = pool.findAll { it.name ==~ regex }
                t.cases.addAll(hit); pool.removeAll(hit)
            }
            cls.children.findAll { !it.test }.each { apply(it) }
        }

        static java.util.regex.Pattern templateRegex(Node t) {
            String display = t.displayName ?: t.method
            StringBuilder re = new StringBuilder()
            def m = (t.nameTemplate =~ /\{([^}]*)\}/)
            int last = 0
            while (m.find()) {
                re << java.util.regex.Pattern.quote(t.nameTemplate.substring(last, m.start()))
                re << (m.group(1) == 'displayName' ? java.util.regex.Pattern.quote(display) : '.*?')
                last = m.end()
            }
            re << java.util.regex.Pattern.quote(t.nameTemplate.substring(last))
            java.util.regex.Pattern.compile(re.toString(), java.util.regex.Pattern.DOTALL)
        }
    }

    // ---------------- 소스 파서 ----------------

    static class Tok {
        char kind   // 'i' 식별자·숫자, 's' 문자열, 'p' 기호
        String text
        String toString() { "$kind:$text" }
    }

    static class SourceParser {
        final String src
        final String path
        List<Tok> toks = []

        SourceParser(String src, String path) { this.src = src; this.path = path }

        List<Node> parse() {
            tokenize()
            Map<String, String> constants = [:]   // 이 파일의 String 상수(@RepeatedTest(name = REPEAT) 같은 참조용)
            for (int c = 0; c + 3 < toks.size(); c++) {
                if (toks[c].text == 'String' && toks[c + 1].kind == 'i' && toks[c + 2].text == '=' && toks[c + 3].kind == 's') {
                    constants[toks[c + 1].text] = toks[c + 3].text
                }
            }
            String pkg = ''
            List<Map> pending = []          // [name, display, template]
            Map declPending = null           // [name, annotations]
            List<Map> stack = []             // [depth, node, fqcn]
            List<Node> tops = []
            int depth = 0
            for (int i = 0; i < toks.size(); i++) {
                Tok t = toks[i]
                if (t.kind == 'i' && t.text == 'package' && depth == 0) {
                    StringBuilder p = new StringBuilder()
                    while (++i < toks.size() && toks[i].text != ';') p << toks[i].text
                    pkg = p.toString()
                    continue
                }
                if (t.kind == 'p' && t.text == '@' && i + 1 < toks.size() && toks[i + 1].kind == 'i' && toks[i + 1].text != 'interface') {
                    int j = i + 1
                    String name = toks[j].text
                    while (j + 2 < toks.size() && toks[j + 1].text == '.' && toks[j + 2].kind == 'i') { j += 2; name = toks[j].text }
                    Map ann = [name: name]
                    if (j + 1 < toks.size() && toks[j + 1].text == '(') {
                        int k = j + 2, paren = 1
                        List<Tok> args = []
                        while (k < toks.size() && paren > 0) {
                            if (toks[k].text == '(' && toks[k].kind == 'p') paren++
                            if (toks[k].text == ')' && toks[k].kind == 'p') paren--
                            if (paren > 0) args << toks[k]
                            k++
                        }
                        j = k - 1
                        ann.display = concat(args, 0, constants)
                        int n = args.findIndexOf { it.kind == 'i' && it.text == 'name' }
                        if (n >= 0 && n + 1 < args.size() && args[n + 1].text == '=') ann.template = concat(args, n + 2, constants)
                    }
                    pending << ann
                    i = j
                    continue
                }
                if (t.kind == 'i' && t.text in ['class', 'interface', 'enum', 'record'] && !(i > 0 && toks[i - 1].text == '.')
                        && i + 1 < toks.size() && toks[i + 1].kind == 'i') {
                    declPending = [name: toks[i + 1].text, annotations: pending]
                    pending = []
                    i++
                    continue
                }
                if (t.kind == 'p' && t.text == '{') {
                    depth++
                    if (declPending != null) {
                        String simple = declPending.name
                        String display = declPending.annotations.find { it.name == 'DisplayName' }?.display
                        String fqcn = stack ? "${stack[-1].fqcn}\$${simple}" : (pkg ? "${pkg}.${simple}" : simple)
                        Node node = new Node(title: display ?: simple, fqcn: fqcn, path: path)
                        if (stack) stack[-1].node.children << node else tops << node
                        stack << [depth: depth, node: node, fqcn: fqcn]
                        declPending = null
                    }
                    pending = []
                    continue
                }
                if (t.kind == 'p' && t.text == '}') {
                    if (stack && stack[-1].depth == depth) stack.remove(stack.size() - 1)
                    depth--
                    pending = []
                    continue
                }
                if (t.kind == 'p' && t.text == ';') { pending = []; continue }
                if (t.kind == 'i' && declPending == null && stack && depth == stack[-1].depth
                        && i + 1 < toks.size() && toks[i + 1].text == '('
                        && pending.any { it.name in TEST_ANNOTATIONS }) {
                    String display = pending.find { it.name == 'DisplayName' }?.display
                    String template = pending.find { it.name in ['ParameterizedTest', 'RepeatedTest'] }?.template
                    if (template == null && pending.any { it.name == 'ParameterizedTest' }) template = '[{index}] {arguments}'
                    if (template == null && pending.any { it.name == 'RepeatedTest' }) template = 'repetition {currentRepetition} of {totalRepetitions}'
                    String title = display ?: (template ? template.replaceAll(/\{[^}]*\}/, '…') : t.text)
                    stack[-1].node.children << new Node(title: title, test: true, method: t.text, nameTemplate: template, displayName: display)
                    pending = []
                }
            }
            tops
        }

        /** from 위치부터 쉼표/끝 전까지 문자열 리터럴과 상수를 이어 붙인다(`"a" + "b"`, `REPEAT`). */
        static String concat(List<Tok> args, int from, Map<String, String> constants) {
            StringBuilder sb = new StringBuilder()
            boolean any = false
            for (int k = from; k < args.size(); k++) {
                Tok a = args[k]
                if (a.kind == 'p' && a.text == ',') break
                if (a.kind == 's') { sb << a.text; any = true }
                else if (a.kind == 'i' && constants.containsKey(a.text)) { sb << constants[a.text]; any = true }
                else if (a.kind == 'i' && from == 0) return null   // name = … 같은 이름 붙은 인자 — 값 하나짜리가 아니다
            }
            any ? sb.toString() : null
        }

        void tokenize() {
            int i = 0, n = src.length()
            while (i < n) {
                char c = src.charAt(i)
                if (Character.isWhitespace(c)) { i++; continue }
                if (c == '/' as char && i + 1 < n && src.charAt(i + 1) == '/' as char) {
                    while (i < n && src.charAt(i) != '\n' as char) i++
                    continue
                }
                if (c == '/' as char && i + 1 < n && src.charAt(i + 1) == '*' as char) {
                    int end = src.indexOf('*/', i + 2)
                    i = end < 0 ? n : end + 2
                    continue
                }
                if (c == '"' as char) {
                    if (src.startsWith('"""', i)) {
                        int end = src.indexOf('"""', i + 3)
                        if (end < 0) end = n
                        toks << new Tok(kind: 's' as char, text: src.substring(i + 3, end).stripIndent().trim())
                        i = end + 3
                        continue
                    }
                    StringBuilder sb = new StringBuilder()
                    i++
                    while (i < n && src.charAt(i) != '"' as char) {
                        char d = src.charAt(i)
                        if (d == '\\' as char && i + 1 < n) {
                            char e = src.charAt(++i)
                            switch (e) {
                                case 'n' as char: sb << '\n'; break
                                case 't' as char: sb << '\t'; break
                                case 'u' as char:
                                    while (i + 1 < n && src.charAt(i + 1) == 'u' as char) i++
                                    sb << (char) Integer.parseInt(src.substring(i + 1, i + 5), 16); i += 4; break
                                default: sb << e
                            }
                        } else sb << d
                        i++
                    }
                    i++
                    toks << new Tok(kind: 's' as char, text: sb.toString())
                    continue
                }
                if (c == '\'' as char) {
                    i++
                    while (i < n && src.charAt(i) != '\'' as char) { if (src.charAt(i) == '\\' as char) i++; i++ }
                    i++
                    toks << new Tok(kind: 'i' as char, text: "'c'")
                    continue
                }
                if (Character.isJavaIdentifierStart(c) || Character.isDigit(c)) {
                    int s = i
                    while (i < n && Character.isJavaIdentifierPart(src.charAt(i))) i++
                    toks << new Tok(kind: 'i' as char, text: src.substring(s, i))
                    continue
                }
                toks << new Tok(kind: 'p' as char, text: String.valueOf(c))
                i++
            }
        }
    }
}
