package pgmreport.config;


import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

//載入外部 app.ini 至 Spring Environment
@Configuration
@PropertySource("file:C:\\Users\\igaluser\\Desktop\\thomas\\pgmreport20251202\\app.ini")
public class IniConfig {
	// 僅負責載入設定檔，無需宣告 Bean
}

