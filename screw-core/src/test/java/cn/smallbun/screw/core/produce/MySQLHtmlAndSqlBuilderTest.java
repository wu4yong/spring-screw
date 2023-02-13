/*
 * screw-core - 简洁好用的数据库表结构文档生成工具
 * Copyright © 2020 SanLi (qinggang.zuo@gmail.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package cn.smallbun.screw.core.example;

import cn.smallbun.screw.core.Configuration;
import cn.smallbun.screw.core.engine.EngineConfig;
import cn.smallbun.screw.core.engine.EngineFileType;
import cn.smallbun.screw.core.engine.EngineTemplateType;
import cn.smallbun.screw.core.execute.DocumentationExecute;
import cn.smallbun.screw.core.process.ProcessConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 导出mysql html和建表语句
 * @author 香菜
 */
public class MySQLHtmlAndSqlBuilderTest {
    public static void main(String[] ags) throws IOException, SQLException {

        String dbName = "fate";
        HikariConfig config = new HikariConfig();
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        config.setJdbcUrl("jdbc:mysql://127.0.0.1:3306/" + dbName + " ?serverTimezone=UTC");
        config.setUsername("root");
        config.setPassword("root");
        config.addDataSourceProperty("useInformationSchema", "true");
        config.setMinimumIdle(2);
        config.setMaximumPoolSize(5);
        DataSource ds = new HikariDataSource(config);
        String userDir = System.getProperty("user.dir") + "\\src\\test\\java\\com\\pdool\\";
        System.out.println(userDir);
        SimpleDateFormat dataFormat = new SimpleDateFormat("yyyyMMdd");
        String versionStr = dataFormat.format(new Date());
        List<String> ignoreTable = new ArrayList<>();
        List<String> ignorePrefix = new ArrayList<>();
        List<String> ignoreSuffix = new ArrayList<>();
        ignoreSuffix.add("_test");
        ignoreSuffix.add("test");

        for (int i = 0; i < 10; i++) {
            ignoreSuffix.add(String.valueOf(i));
        }
        createHtml(ds, userDir, versionStr, ignoreTable, ignorePrefix, ignoreSuffix);
        createSql(dbName, ds, userDir, versionStr, ignoreTable, ignorePrefix, ignoreSuffix);
    }

    /**
     * 创建html
     * @param dataSource
     * @param userDir
     * @param versionStr
     * @param ignoreTable
     * @param ignorePrefix
     * @param ignoreSuffix
     */
    public static void createHtml(DataSource dataSource, String userDir, String versionStr,
                                  List<String> ignoreTable, List<String> ignorePrefix,
                                  List<String> ignoreSuffix) {

        EngineConfig engineConfig = EngineConfig.builder().fileOutputDir(userDir)
            .openOutputDir(false).fileType(EngineFileType.HTML)
            .produceType(EngineTemplateType.freemarker).build();

        ProcessConfig processConfig = ProcessConfig.builder().ignoreTableName(ignoreTable)
            .ignoreTablePrefix(ignorePrefix).ignoreTableSuffix(ignoreSuffix).build();

        Configuration config = Configuration.builder().version(versionStr).description("数据库文档")
            .dataSource(dataSource).engineConfig(engineConfig).produceConfig(processConfig).build();

        new DocumentationExecute(config).execute();
    }

    /**
     * 生成建表sql
     * @param dbName
     * @param dataSource
     * @param userDir
     * @param versionStr
     * @param ignoreTable
     * @param ignorePrefix
     * @param ignoreSuffix
     * @throws IOException
     * @throws SQLException
     */
    public static void createSql(String dbName, DataSource dataSource, String userDir,
                                 String versionStr, List<String> ignoreTable,
                                 List<String> ignorePrefix,
                                 List<String> ignoreSuffix) throws IOException, SQLException {
        Statement tmt = null;
        PreparedStatement pstmt = null;
        List<String> createSqlList = new ArrayList<>();
        String sql = "select TABLE_NAME from INFORMATION_SCHEMA.TABLES where TABLE_SCHEMA = '"
                     + dbName + "' and TABLE_TYPE = 'BASE TABLE'";
        tmt = dataSource.getConnection().createStatement();
        pstmt = dataSource.getConnection().prepareStatement(sql);
        ResultSet res = tmt.executeQuery(sql);
        while (res.next()) {
            String tableName = res.getString(1);
            if (tableName.contains("`")) {
                continue;
            }
            if (ignoreTable.contains(tableName)) {
                continue;
            }
            boolean isContinue = false;
            for (String prefix : ignorePrefix) {

                if (tableName.startsWith(prefix)) {
                    isContinue = true;
                    break;
                }
            }
            if (isContinue) {
                continue;
            }
            for (String suffix : ignoreSuffix) {
                if (tableName.startsWith(suffix)) {
                    isContinue = true;
                    break;
                }
            }
            if (isContinue) {
                continue;
            }
            ResultSet rs = pstmt.executeQuery("show create Table `" + tableName + "`");

            while (rs.next()) {
                createSqlList.add("DROP TABLE IF EXISTS '" + tableName + "'");
                createSqlList.add(rs.getString(2));
            }
        }

        String head = "-- 数据库建表语句 \r\n";
        head += "-- db:" + dbName + " version: " + versionStr + "\r\n";
        String collect = String.join(";\r\n", createSqlList);
        collect = head + collect + ";";
        string2file(collect, userDir + dbName + "_" + versionStr + ".sql");
    }

    public static void string2file(String collect, String dirStr) throws IOException {
        System.out.println("文件地址  " + dirStr);
        OutputStreamWriter osw = null;
        try {
            osw = new OutputStreamWriter(new FileOutputStream(new File(dirStr)),
                StandardCharsets.UTF_8);
            osw.write(collect);
            osw.flush();
        } finally {
            if (osw != null) {
                osw.close();
            }
        }
    }
}
