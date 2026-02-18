package pgmreport.service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

//指示檔配信失敗門市查詢服務（IGAL）
@Service
public class FailStoreExportService {
	//日期格式：yyyyMMdd
    private static final DateTimeFormatter DATE_YYYYMMDD =
            DateTimeFormatter.BASIC_ISO_DATE; // yyyyMMdd
    // IGAL 資料庫 JdbcTemplate
    private final JdbcTemplate igalJdbc;
    // 輸出目錄（目前僅保留設定，不一定使用）
    private final Path outputDir; 

    public FailStoreExportService(
            @Qualifier("igalJdbcTemplate") JdbcTemplate igalJdbcTemplate,
            @Value("${report.xlsx.output-dir}") String outputDir) {

        this.igalJdbc = igalJdbcTemplate;
        this.outputDir = Paths.get(outputDir);
    }

    // 依日期查詢「指示檔配信失敗門市」清單（提供給 Excel 匯出）
    public List<FailStoreRow> findFailStores(LocalDate date) {
        String dateStr = date.format(DATE_YYYYMMDD); // 20230109
        return queryFailStores(dateStr);
    }

    // 實際執行 SQL 查詢
    private List<FailStoreRow> queryFailStores(String dateYmd) {
        String sql = """
            SELECT A.trm_id, B.dlf_ip1, B.adsl_1
            FROM send_file_kanri A
            JOIN ig_ui_sc_t B
              ON A.trm_id = '00' || B.store
            WHERE A.unyo_f_name LIKE 'SDCDGETR%%'
              AND A.file_sts = '7'
              AND TO_CHAR(A.kidou_date, 'YYYYMMDD') = ?
            """;

        return igalJdbc.query(sql,
                (rs, rowNum) -> new FailStoreRow(
                        rs.getString("trm_id"),
                        rs.getString("dlf_ip1"),
                        rs.getString("adsl_1")
                ),
                dateYmd);
    }

    // 查詢結果用 DTO
    public record FailStoreRow(String trmId, String dlfIp1, String adsl1) {}
}
