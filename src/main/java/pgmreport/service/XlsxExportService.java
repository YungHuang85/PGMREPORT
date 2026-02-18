package pgmreport.service;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/*
 * 緊急復舊失敗門市 Excel 匯出服務
 * - 組合多來源資料
 * - 產出單一 XLSX（多 Sheet）
 */
@Service
public class XlsxExportService {
	// 檔名用日期格式：yyyyMMdd
    private static final DateTimeFormatter DATE_YYYYMMDD =
            DateTimeFormatter.BASIC_ISO_DATE; // yyyyMMdd
    
    // 失敗門市資料來源
    private final FailStoreExportService failStoreService;
    // NBITS 未取檔資料來源
    private final NbitsDiffExportService nbitsDiffService;
    // XLSX 輸出目錄（由設定檔注入）
    private final Path outputDir;

    public XlsxExportService(
            FailStoreExportService failStoreService,
            NbitsDiffExportService nbitsDiffService,
            @Value("${report.xlsx.output-dir}") String outputDir) { //Value來自app.ini
        this.failStoreService = failStoreService;
        this.nbitsDiffService = nbitsDiffService;
        this.outputDir = Paths.get(outputDir);
    }

    /*
     * 產生緊急復舊 XLSX
     * - Sheet1：指示檔配信失敗門市
     * - Sheet2：NBITS 未取檔門市
     */
    public Path exportEmergencyXlsx(LocalDate date) throws IOException {
    	// 查詢資料
        List<FailStoreExportService.FailStoreRow> failRows =  // 宣告並取得「配信失敗」資料列集合
                failStoreService.findFailStores(date);  // 呼叫 failStoreService 依日期查詢失敗門市

        List<NbitsDiffExportService.Row> nbitsDiffRows =  // 宣告並取得「NBITS 未取檔」資料列集合
                nbitsDiffService.findNbitsDiffRows(date);  // 呼叫 nbitsDiffService 依日期查詢差異/未取檔

        // 組輸出檔名與路徑
        Files.createDirectories(outputDir);  // 確保輸出資料夾存在（不存在就建立，已存在不報錯）
        String dateStr = date.format(DATE_YYYYMMDD);  // 將日期格式化成 yyyyMMdd
        String fileName = dateStr + "緊急復舊配信失敗門市.xlsx";  // 組出輸出檔名（含日期前綴）
        Path xlsxPath = outputDir.resolve(fileName);  // 在 outputDir 底下組成完整檔案路徑

        try (Workbook workbook = new XSSFWorkbook()) {  // 建立 XLSX 工作簿，try-with-resources 自動關閉資源
            // Sheet1：指示檔配信失敗門市
            Sheet sheet1 = workbook.createSheet("指示檔配信失敗門市"); // 建立第一張工作表並命名
            createHeaderRow(sheet1); // 建立表頭列（第 0 列
            int rowIdx = 1; // 從第 1 列開始填資料（第 0 列留給表頭）
            for (FailStoreExportService.FailStoreRow r : failRows) { // 逐筆確認失敗門市資料
                Row row = sheet1.createRow(rowIdx++); // 建立新資料列並將 rowIdx 自增
                row.createCell(0).setCellValue(nullSafe(r.trmId())); // 第 0 欄：TRM_ID
                row.createCell(1).setCellValue(nullSafe(r.dlfIp1())); // 第 1 欄：DLF_IP1
                row.createCell(2).setCellValue(nullSafe(r.adsl1())); // 第 2 欄：ADSL_1
            }

            // Sheet2：NBITS 未取檔門市
            Sheet sheet2 = workbook.createSheet("NBITS未取檔門市"); // 建立第二張工作表並命名
            createHeaderRow(sheet2); // 建立表頭列（第 0 列）
            rowIdx = 1; // 重設 rowIdx，從第 1 列開始填第二張表
            for (NbitsDiffExportService.Row r : nbitsDiffRows) { // 逐筆確認 NBITS 差異/未取檔資料
                Row row = sheet2.createRow(rowIdx++); // 建立新資料列並將 rowIdx 自增
                row.createCell(0).setCellValue(nullSafe(r.trmId())); // 第 0 欄：TRM_ID
                row.createCell(1).setCellValue(nullSafe(r.dlfIp1())); // 第 1 欄：DLF_IP1
                row.createCell(2).setCellValue(nullSafe(r.adsl1())); // 第 2 欄：ADSL_1
            }
            
            // 自動調整欄寬,共3欄
            autoSizeColumns(sheet1, 3);
            autoSizeColumns(sheet2, 3);
            
            // 寫出 XLSX
            try (var out = Files.newOutputStream(xlsxPath,  // 開啟輸出串流，指向目標 XLSX 路徑
                    StandardOpenOption.CREATE,  // 若檔案不存在則建立
                    StandardOpenOption.TRUNCATE_EXISTING,   // 若檔案已存在則清空後重寫
                    StandardOpenOption.WRITE)) {   // 以寫入模式開啟檔案
                workbook.write(out);  // 將記憶體中的 Workbook 內容寫入檔案
            }
        }
        // 回傳產生的 XLSX 檔案路徑
        return xlsxPath;
    }
    
    // 建立表頭列
    private void createHeaderRow(Sheet sheet) { //在指定 sheet 建立表頭
        Row header = sheet.createRow(0);  // 建立第 0 列作為表頭列
        header.createCell(0).setCellValue("TRM_ID");  // 表頭第 0 欄名稱
        header.createCell(1).setCellValue("DLF_IP1");  // 表頭第 1 欄名稱
        header.createCell(2).setCellValue("ADSL_1");  // 表頭第 2 欄名稱
    }

    // 欄寬自動調整
    private void autoSizeColumns(Sheet sheet, int columnCount) { // 自動調整指定欄數的欄寬
        for (int i = 0; i < columnCount; i++) { // 迴圈走訪 0..columnCount-1
            sheet.autoSizeColumn(i);  // 讓 Excel 欄寬依內容自動調整
        }
    }
    
    // 防止 null 寫入 Excel
    private String nullSafe(String v) {
        return v == null ? "" : v;
    }
}
