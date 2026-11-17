import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * v1.6의 H2 파일(`yona.h2.db`, H2 1.3 PageStore 형식)을 읽어 v2의 데이터 가져오기가 받는 JSON 파일(`/sites/export`와 같은 모양)로 쓴다.
 * v2가 쓰는 H2 2.x는 1.3 파일을 읽지 못하므로, v1.6이 쓰던 옛 H2 jar(`lib/com.h2database.h2-1.3.176.jar`)로 읽는다. v1.6을 켤 필요가 없고 관리자 로그인도 필요 없다.
 *
 * 사용법: {@code java -cp H2Export.class가 있는 폴더:h2-1.3.176.jar H2Export <yona.h2.db 또는 확장자를 뺀 경로> <출력.json> [사용자 [비밀번호]]}
 * 의존하는 것은 JDBC와 옛 H2 jar뿐이다. Java 8에서 컴파일되고 실행된다(v1.6이 쓰던 Java로 돌릴 수 있다).
 *
 * <ul>
 *   <li><b>원본을 건드리지 않는다.</b> 파일을 임시 폴더에 복사해 그 복사본을 열고 끝나면 지운다. v1.6 서버는 꺼져 있어야 한다(켜져 있으면 파일이 일관되지 않을 수 있다).</li>
 *   <li>모양: 최상위 객체에 테이블 이름(대문자)마다 행의 배열, 시퀀스가 있는 테이블은 그 뒤에 {@code <이름>_SEQ}(가장 큰 번호 + 1). 행의 키는 소문자 컬럼 이름이다.</li>
 *   <li>값: 정수는 숫자, 문자열과 CLOB은 문자열, 시각과 날짜는 에폭 밀리초, 불린은 true/false(NULL은 false), DB NULL은 JSON null. 시각은 이 프로그램을 실행하는 JVM의 시간대로
 *       읽는다. v1.6 서버와 같은 시간대여야 하며 다르면 {@code -Duser.timezone=Asia/Seoul} 처럼 정한다.</li>
 *   <li>모든 테이블을 쓴다. v2의 가져오기는 모르는 테이블과 컬럼을 건너뛴다.</li>
 * </ul>
 *
 * 종료 코드: 0 성공, 2 사용법이나 파일 오류, 3 이미 H2 2.x 형식(`yona.mv.db`)이라 변환이 필요 없음, 1 그 밖의 실패.
 */
public class H2Export {

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("사용법: H2Export <yona.h2.db 또는 확장자를 뺀 경로> <출력.json> [사용자 [비밀번호]]");
            System.exit(2);
        }
        try {
            System.exit(run(args));
        } catch (Exception e) {
            System.err.println("변환에 실패했습니다: " + e);
            e.printStackTrace(System.err);
            System.exit(1);
        }
    }

    static int run(String[] args) throws Exception {
        String base = args[0].endsWith(".h2.db") ? args[0].substring(0, args[0].length() - ".h2.db".length()) : args[0];
        File source = new File(base + ".h2.db");
        if (!source.isFile()) {
            if (new File(base + ".mv.db").isFile()) {
                System.err.println(base + ".mv.db는 이미 H2 2.x 형식입니다. 변환이 필요 없습니다(v2가 그대로 읽습니다).");
                return 3;
            }
            System.err.println("H2 파일이 없습니다: " + source);
            return 2;
        }
        File output = new File(args[1]);
        String user = args.length > 2 ? args[2] : "";
        String password = args.length > 3 ? args[3] : "";

        File work = Files.createTempDirectory("yona-h2-export").toFile();
        try {
            // 원본을 건드리지 않도록 복사본을 연다(큰 객체 폴더가 있으면 함께 복사한다)
            Files.copy(source.toPath(), new File(work, "yona.h2.db").toPath(), StandardCopyOption.REPLACE_EXISTING);
            File lobs = new File(base + ".lobs.db");
            if (lobs.isDirectory()) copyDirectory(lobs, new File(work, "yona.lobs.db"));

            Class.forName("org.h2.Driver");
            String url = "jdbc:h2:file:" + new File(work, "yona").getAbsolutePath().replace('\\', '/') + ";IFEXISTS=TRUE";
            try (Connection connection = DriverManager.getConnection(url, user, password)) {
                export(connection, output);
                try (Statement st = connection.createStatement()) { st.execute("SHUTDOWN"); } catch (Exception ignored) { }
            }
            return 0;
        } finally {
            deleteRecursively(work);
        }
    }

    static void export(Connection connection, File output) throws Exception {
        List<String> tables = new ArrayList<String>();
        Set<String> sequences = new HashSet<String>();
        try (Statement st = connection.createStatement()) {
            try (ResultSet rs = st.executeQuery("select TABLE_NAME from INFORMATION_SCHEMA.TABLES where TABLE_SCHEMA = 'PUBLIC' and TABLE_TYPE = 'TABLE' order by TABLE_NAME")) {
                while (rs.next()) tables.add(rs.getString(1));
            }
            try (ResultSet rs = st.executeQuery("select SEQUENCE_NAME from INFORMATION_SCHEMA.SEQUENCES")) {
                while (rs.next()) sequences.add(rs.getString(1).toUpperCase());
            }
        }
        try (BufferedWriter out = new BufferedWriter(new OutputStreamWriter(Files.newOutputStream(output.toPath()), StandardCharsets.UTF_8), 1 << 16)) {
            out.write("{");
            boolean firstTable = true;
            for (String table : tables) {
                if (!firstTable) out.write(",");
                firstTable = false;
                long rows = writeTable(connection, table, out);
                System.out.println(table + " " + rows);
                if (sequences.contains(table.toUpperCase() + "_SEQ")) {
                    out.write(",\"" + table + "_SEQ\":" + nextId(connection, table));
                }
            }
            out.write("}");
        }
    }

    /** 테이블 하나를 행의 배열로 쓰고 행 수를 돌려준다. */
    static long writeTable(Connection connection, String table, BufferedWriter out) throws Exception {
        out.write("\"" + table + "\":[");
        long count = 0;
        try (Statement st = connection.createStatement()) {
            st.setFetchSize(1000);
            try (ResultSet rs = st.executeQuery("select * from \"" + table + "\"")) {
                ResultSetMetaData meta = rs.getMetaData();
                int columns = meta.getColumnCount();
                String[] names = new String[columns + 1];
                int[] types = new int[columns + 1];
                for (int i = 1; i <= columns; i++) {
                    names[i] = meta.getColumnName(i).toLowerCase();
                    types[i] = meta.getColumnType(i);
                }
                while (rs.next()) {
                    if (count++ > 0) out.write(",");
                    out.write("{");
                    for (int i = 1; i <= columns; i++) {
                        if (i > 1) out.write(",");
                        out.write("\"" + names[i] + "\":" + value(rs, i, types[i]));
                    }
                    out.write("}");
                }
            }
        }
        out.write("]");
        return count;
    }

    /** 컬럼 값을 JSON으로 쓴다. */
    static String value(ResultSet rs, int index, int type) throws Exception {
        switch (type) {
            case Types.BIGINT: case Types.INTEGER: case Types.SMALLINT: case Types.TINYINT: {
                long v = rs.getLong(index);
                return rs.wasNull() ? "null" : Long.toString(v);
            }
            case Types.BOOLEAN: case Types.BIT:
                return rs.getBoolean(index) ? "true" : "false"; // DB NULL은 false이다(v1.6 내보내기와 같다)
            case Types.TIMESTAMP: {
                java.sql.Timestamp v = rs.getTimestamp(index);
                return v == null ? "null" : Long.toString(v.getTime());
            }
            case Types.DATE: {
                java.sql.Date v = rs.getDate(index);
                return v == null ? "null" : Long.toString(v.getTime());
            }
            case Types.DECIMAL: case Types.NUMERIC: case Types.DOUBLE: case Types.FLOAT: case Types.REAL: {
                String v = rs.getString(index);
                return v == null ? "null" : v;
            }
            default: {
                String v = rs.getString(index);
                return v == null ? "null" : quote(v);
            }
        }
    }

    /** 가장 큰 번호 + 1이다(v1.6 내보내기가 H2에서 쓰던 값, 행이 없으면 1). */
    static long nextId(Connection connection, String table) throws Exception {
        try (Statement st = connection.createStatement()) {
            try (ResultSet rs = st.executeQuery("select max(ID) from \"" + table + "\"")) {
                if (rs.next()) {
                    long max = rs.getLong(1);
                    return rs.wasNull() ? 1 : max + 1;
                }
            }
        } catch (Exception e) {
            // ID 컬럼이 없는 테이블이다
        }
        return 1;
    }

    static String quote(String text) {
        StringBuilder sb = new StringBuilder(text.length() + 2).append('"');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c)); else sb.append(c);
            }
        }
        return sb.append('"').toString();
    }

    static void copyDirectory(File from, File to) throws IOException {
        File[] children = from.listFiles();
        to.mkdirs();
        if (children == null) return;
        for (File child : children) {
            File target = new File(to, child.getName());
            if (child.isDirectory()) copyDirectory(child, target);
            else Files.copy(child.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    static void deleteRecursively(File file) {
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteRecursively(child);
        file.delete();
    }
}
