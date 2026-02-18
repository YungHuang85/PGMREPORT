package pgmreport.config;

import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

 /* DataSource 設定類
 * 功能：
 * - 手動定義多組 DataSource（IGAL / NBITS）
 * - 搭配 JdbcTemplate 使用純 JDBC 查詢
 */

//多資料庫連線與 JdbcTemplate 組態集中於此
//讓 Spring 在啟動時載入此設定類並註冊 Bean
@Configuration
public class DataSourceConfig {

    // ---------- IGAL ----------
	// 註冊一個 DataSourceProperties Bean（名稱：igalDataSourceProperties）
    @Bean
    @ConfigurationProperties("spring.datasource.igal")
    public DataSourceProperties igalDataSourceProperties() {
        return new DataSourceProperties();
    }
    // 註冊一個 IGAL DataSource Bean（名稱：igalDataSource）
    @Bean
    public DataSource igalDataSource(
            @Qualifier("igalDataSourceProperties") DataSourceProperties props) {
        // 這行會把 url / username / password / driver-class-name 全部套到 DataSource
        return props.initializeDataSourceBuilder().build();
    }
    // 註冊一個 IGAL JdbcTemplate Bean（名稱：igalJdbcTemplate）
    @Bean
    public JdbcTemplate igalJdbcTemplate(
            @Qualifier("igalDataSource") DataSource ds) {
        return new JdbcTemplate(ds);
    }

    // ---------- NBITS ----------
    // 註冊一個 DataSourceProperties Bean（名稱：nbitsDataSourceProperties）
    @Bean
    @ConfigurationProperties("spring.datasource.nbits")
    public DataSourceProperties nbitsDataSourceProperties() {
        return new DataSourceProperties();
    }
    
    // 註冊一個 NBITS DataSource Bean（名稱：nbitsDataSource）
    @Bean 
    public DataSource nbitsDataSource(
            @Qualifier("nbitsDataSourceProperties") DataSourceProperties props) {
        return props.initializeDataSourceBuilder().build();
    }
    
    // 註冊一個 NBITS JdbcTemplate Bean（名稱：nbitsJdbcTemplate）
    @Bean
    public JdbcTemplate nbitsJdbcTemplate(
            @Qualifier("nbitsDataSource") DataSource ds) {
        return new JdbcTemplate(ds);
    }
}
