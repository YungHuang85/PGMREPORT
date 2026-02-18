package pgmreport;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;

import pgmreport.service.XlsxExportService;
import pgmreport.service.FileStatusReportService;
import pgmreport.service.NbitsDiffExportService;
import pgmreport.service.FileStatusReportService.FileStatusResult;

import java.nio.file.Path;
import java.time.LocalDate;

/*
 * Spring Boot 主程式
 *
 * - 使用 CommandLineRunner：啟動即執行一次批次邏輯
 * - 排除預設 JDBC AutoConfig：避免 Spring 嘗試建立預設 DataSource
 */
@SpringBootApplication(
        exclude = {
                DataSourceAutoConfiguration.class,
                JdbcTemplateAutoConfiguration.class
        }
)
public class PgmreportApplication implements CommandLineRunner {

    private final FileStatusReportService reportService;
    private final NbitsDiffExportService nbitsDiffService;
    private final XlsxExportService emergencyXlsxExportService;

    /*
     * 建構子注入（Constructor Injection）
     * - 符合 SOLID / 可測試性
     * - 避免 Field Injection
     */
    public PgmreportApplication(FileStatusReportService reportService,
                                        NbitsDiffExportService nbitsDiffService,
                                        XlsxExportService emergencyXlsxExportService) {
        this.reportService = reportService;
        this.nbitsDiffService = nbitsDiffService;
        this.emergencyXlsxExportService = emergencyXlsxExportService;
    }

    //Spring Boot 進入點
    public static void main(String[] args) {
        SpringApplication.run(PgmreportApplication.class, args);
    }

    @Override
    public void run(String... args) {
        try {
        	// 解析查詢日期（有參數用參數，否則用今天）
            LocalDate date = (args.length > 0)
                    ? LocalDate.parse(args[0])
                    : LocalDate.now();

            // 查詢指示檔配信整體結果
            FileStatusResult r = reportService.queryByDate(date);

            // 計算 NBITS 未取檔門市數量
            int nbitsNotFetchedCount = nbitsDiffService.countNbitsDiff(date);

            // 產生 xlsx（兩個 sheet）
            Path xlsxPath = emergencyXlsxExportService.exportEmergencyXlsx(date);

            // Console 輸出
            System.out.println("===== 指示檔配信查詢結果 =====");
            System.out.println(r.date());
            System.out.println("指示檔配信門市總數: " + r.totalCount());
            System.out.println("指示檔配信成功門市: " + r.successCount());
            System.out.println("指示檔配信失敗門市: " + r.failCount());
            System.out.println("NBITS取檔成功門市: " + r.nbitsSuccess());
            System.out.println("NBITS未取檔門市: " + nbitsNotFetchedCount);
            System.out.println("===============================");
            // XLSX 實體輸出路徑
            System.out.println("緊急復舊配信失敗門市 XLSX 輸出路徑: " + xlsxPath);

        } catch (Exception ex) {
        	// 最外層防護：任何未攔截錯誤都視為錯誤
            System.err.println("[FATAL] 程式執行錯誤：" + ex.getMessage());
            ex.printStackTrace();
        }
    }
}
