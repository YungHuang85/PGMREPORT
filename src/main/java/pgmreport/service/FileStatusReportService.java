package pgmreport.service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/*
 * 指示檔配信狀態彙總查詢服務
 * - 同時查詢 IGAL / NBITS
 * - 組合為單一查詢結果 DTO
 */
@Service
public class FileStatusReportService {

    private final JdbcTemplate igalJdbc;   // IGAL DB
    private final JdbcTemplate nbitsJdbc;  // NBITS DB

    public FileStatusReportService(
    		@Qualifier("igalJdbcTemplate") JdbcTemplate igalJdbcTemplate,
    		@Qualifier("nbitsJdbcTemplate") JdbcTemplate nbitsJdbcTemplate) {
        this.igalJdbc = igalJdbcTemplate;
        this.nbitsJdbc = nbitsJdbcTemplate;
    }

    // 依指定日期查詢指示檔配信與 NBITS 取檔狀態
    public FileStatusResult queryByDate(LocalDate date) {
    	// 將 LocalDate 轉成字串（YYYY-MM-DD），供 SQL 查詢使用
    	String dateParam = date.toString();  
    	// 查詢 IGAL 指示檔配信總門市數
        Integer total   = queryTotal(dateParam);
        // 查詢 IGAL 指示檔配信成功門市數
        Integer success = querySuccess(dateParam);
        // 查詢 IGAL 指示檔配信失敗門市數
        Integer fail    = queryFail(dateParam);
        // 查詢 NBITS 取檔成功門市數
        Integer nbits   = queryNbitsSuccess(dateParam);
        
        // 將查詢結果組成回傳 DTO
        return new FileStatusResult(
                date,  // 查詢日期
                nvl(total),  // 配信總數
                nvl(success),  // 配信成功數
                nvl(fail),  // 配信失敗數
                nvl(nbits)  // NBITS 取檔成功數
        );
    }
    
    // null 安全處理（避免查詢失敗導致NullPointerException）
    private int nvl(Integer v) { return v == null ? 0 : v; }

    // IGAL：指示檔配信總筆數
    private Integer queryTotal(String dateParam) {
        try {
            String sql = """
                SELECT COUNT(*)
                FROM send_file_kanri s
                JOIN ig_ui_sc_t i ON s.trm_id = i.id
                WHERE s.unyo_f_name = 'SDCDGETR'
                  AND TRUNC(s.kidou_date) = TO_DATE(?, 'YYYY-MM-DD')
                """;
            return igalJdbc.queryForObject(sql, Integer.class, dateParam);
        } catch (Exception ex) {
            System.err.println("[ERROR] IGAL-總數查詢失敗：" + ex.getMessage());
            return null;
        }
    }

    // IGAL：配信成功門市數量
    private Integer querySuccess(String dateParam) {
        try {
            String sql = """
                SELECT COUNT(*)
                FROM send_file_kanri s
                JOIN ig_ui_sc_t i ON s.trm_id = i.id
                WHERE s.unyo_f_name = 'SDCDGETR'
                  AND s.file_sts = '8'
                  AND TRUNC(s.kidou_date) = TO_DATE(?, 'YYYY-MM-DD')
                """;
            return igalJdbc.queryForObject(sql, Integer.class, dateParam);
        } catch (Exception ex) {
            System.err.println("[ERROR] IGAL-成功查詢失敗：" + ex.getMessage());
            return null;
        }
    }

    // IGAL：配信失敗門市數量
    private Integer queryFail(String dateParam) {
        try {
            String sql = """
                SELECT COUNT(*)
                FROM send_file_kanri A
                JOIN ig_ui_sc_t B
                  ON A.trm_id = '00' || B.store
                WHERE A.unyo_f_name LIKE 'SDCDGETR%'
                  AND A.file_sts = '7'
                  AND TRUNC(A.kidou_date) = TO_DATE(?, 'YYYY-MM-DD')
                """;
            return igalJdbc.queryForObject(sql, Integer.class, dateParam);
        } catch (Exception ex) {
            System.err.println("[ERROR] IGAL-失敗查詢失敗：" + ex.getMessage());
            return null;
        }
    }

    // NBITS：取檔成功門市數量
    private Integer queryNbitsSuccess(String dateParam) {
        try {
            String sql = """
                SELECT COUNT(*)
                FROM nbit_dllog
                WHERE TO_CHAR(log_date, 'YYYY-MM-DD HH24:MI:SS') LIKE ?
                  AND file_id = 'SDTDRCV3'
                  AND status = '2'
            """;
            return nbitsJdbc.queryForObject(sql, Integer.class, dateParam + "%");
        } catch (Exception ex) {
            System.err.println("[ERROR] NBITS-成功查詢失敗：" + ex.getMessage());
            return null;
        }
    }

    // 配信狀態彙總 DTO
    public record FileStatusResult(
            LocalDate date,
            int totalCount,
            int successCount,
            int failCount,
            int nbitsSuccess
    ) {}
}
