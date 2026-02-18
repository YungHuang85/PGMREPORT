package pgmreport.service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/*
 * NBITS 差異門市查詢服務
 * 比對 IGAL 配信成功 vs NBITS 取檔成功
 * 找出「IGAL 成功但 NBITS 未取檔」門市
 */
@Service
public class NbitsDiffExportService {
	// 日期格式：yyyyMMdd
    private static final DateTimeFormatter DATE_YYYYMMDD =
            DateTimeFormatter.BASIC_ISO_DATE; // yyyyMMdd
    // IGAL 資料庫
    private final JdbcTemplate igalJdbc;
    // NBITS 資料庫
    private final JdbcTemplate nbitsJdbc;
    // 輸出目錄
    private final Path outputDir;

    // igalJdbcTemplate 與 nbitsJdbcTemplate 這兩個 Bean，被注入到 NbitsDiffCsvExportService 裡，供該 Service 內部使用。
    public NbitsDiffExportService(
            @Qualifier("igalJdbcTemplate") JdbcTemplate igalJdbcTemplate,
            @Qualifier("nbitsJdbcTemplate") JdbcTemplate nbitsJdbcTemplate,
            @Value("${report.xlsx.output-dir}") String outputDir) {

        this.igalJdbc = igalJdbcTemplate;
        this.nbitsJdbc = nbitsJdbcTemplate;
        this.outputDir = Paths.get(outputDir);
    }

    // 查詢「NBITS 未取檔」門市明細（給 Excel 用）
    public List<Row> findNbitsDiffRows(LocalDate date) {  // 依指定日期找出「IGAL 成功但 NBITS 未成功」的門市明細
        String dateStr   = date.toString();  // 將 LocalDate 轉成字串，格式為 yyyy-MM-dd（例如 2025-12-17）
        String likeParam = dateStr + "%";  // // 組成 SQL LIKE 參數（例如 2025-12-17%），用於比對 NBITS log_date 前綴

        // 1. IGAL 配信成功門市
        List<String> igalStores = queryIgalSuccessStores(dateStr); // 從 IGAL 查出「當日配信成功」的門市清單

        // 2. NBITS 取檔成功門市
        List<String> nbitsStores = queryNbitsSuccessStores(likeParam); // 從 NBITS 查出「當日取檔成功」的門市清單
        Set<String> nbitsStoreSet = new HashSet<>(nbitsStores);

        if (igalStores.isEmpty()) { // 若 IGAL 當日沒有任何成功門市
            return List.of(); // 直接回傳空集合
        }

        // 3. 差集：IGAL 成功，但 NBITS 沒成功的門市
        List<String> diffStores = igalStores.stream() // 將 IGAL 成功門市清單轉成 Stream 進行集合運算
                .filter(s -> !nbitsStoreSet.contains(s))  // 過濾掉「NBITS 也成功」的門市，只保留 NBITS 未成功者
                .distinct()  // 去除重複門市
                .collect(Collectors.toList());  // 將結果收集回 List，作為下一步查明細的輸入

        // 4. 查詢門市詳細資料
        return queryStoreDetails(diffStores);
    }

    // 僅回傳 NBITS 未取檔門市筆數（給主程式顯示）
    public int countNbitsDiff(LocalDate date) {
        return findNbitsDiffRows(date).size();
    }

    // NBITS：取檔成功門市
    private List<String> queryNbitsSuccessStores(String likeParam) {
        String sql = """
            SELECT DISTINCT SUBSTR(term_id, 3, 6) AS store
            FROM nbit_dllog
            WHERE TO_CHAR(log_date, 'YYYY-MM-DD HH24:MI:SS') LIKE ?
              AND file_id = 'SDTDRCV3'
              AND status = '2'
            """;

        return nbitsJdbc.query(sql,
                (rs, rowNum) -> rs.getString("store"),
                likeParam);
    }

    // IGAL：指示檔配信成功門市
    private List<String> queryIgalSuccessStores(String dateParam) {
        String sql = """
            SELECT DISTINCT SUBSTR(s.trm_id, 3, 6) AS store
            FROM send_file_kanri s
            JOIN ig_ui_sc_t i
              ON s.trm_id = i.id
            WHERE s.unyo_f_name = 'SDCDGETR'
              AND s.file_sts = '8'
              AND TO_CHAR(s.kidou_date, 'YYYY-MM-DD') = ?
            """;

        return igalJdbc.query(sql,
                (rs, rowNum) -> rs.getString("store"),
                dateParam);
    }

    // IGAL：查詢門市明細（TRM_ID / DLF_IP1 / ADSL_1）
    private List<Row> queryStoreDetails(List<String> stores) {
    	// 防呆：沒有門市清單就直接回空結果
    	if (stores == null || stores.isEmpty()) {
            return List.of();
        }
    	// 將門市數量轉成 SQL IN 子句的 ? 佔位符，例如：?, ?, ?
        String placeholders = stores.stream()
                .map(s -> "?")
                .collect(Collectors.joining(","));
     // SQL查詢
        String sql = """
            SELECT SUBSTR(id, 3, 6) AS store,
                   id AS trm_id,
                   dlf_ip1,
                   adsl_1
            FROM ig_ui_sc_t
            WHERE SUBSTR(id, 3, 6) IN ( %s )
            """.formatted(placeholders);
        
	    // 使用 JdbcTemplate 執行查詢
	    // PreparedStatement 建立與參數綁定
        return igalJdbc.query(con -> {
                    var ps = con.prepareStatement(sql); // 建立 PreparedStatement
                    for (int i = 0; i < stores.size(); i++) {
                        ps.setString(i + 1, stores.get(i));  // 依序綁定門市代碼
                    }
                    return ps;  // 回傳已設定完成的 Statement
                },
        		// 將每一筆查詢結果轉成 Row DTO
                (rs, rowNum) -> new Row(
                        rs.getString("trm_id"), // TRM_ID
                        rs.getString("dlf_ip1"), // DLF_IP1
                        rs.getString("adsl_1"), // ADSL_1
                        rs.getString("store") // 門市代碼
                ));
    }

    // NBITS 差異門市明細 DTO
    public record Row(String trmId, String dlfIp1, String adsl1, String store) {}
}
