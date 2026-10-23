package com.library;

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * Main Entry Point for the Library Book Issue Management System.
 * Powered by Spring Boot 3 & Java 21.
 */
@SpringBootApplication
public class LibraryApplication {

    public static void main(String[] args) {
        SpringApplication.run(LibraryApplication.class, args);
    }

    /**
     * Initializes the MySQL database tables from schema.sql on first launch if they don't exist.
     */
    @Bean
    public ApplicationRunner initDatabase(DataSource dataSource) {
        return args -> {
            System.out.println("==================================================================");
            System.out.println("   📚 Library Book Issue Management System (Spring Boot + JDBC)");
            System.out.println("==================================================================");
            try (Connection conn = dataSource.getConnection()) {
                System.out.println(" [OK] Successfully connected to MySQL database: library_db");

                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery("SHOW TABLES LIKE 'books'")) {
                    if (!rs.next()) {
                        System.out.println(" [INIT] Creating tables and seeding sample data from schema.sql...");
                        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(new ClassPathResource("db/schema.sql"));
                        populator.execute(dataSource);
                        System.out.println(" [OK] Database schema initialized successfully!");
                    } else {
                        System.out.println(" [OK] Database tables verified.");
                    }
                }
            } catch (Exception e) {
                System.err.println(" [NOTICE] MySQL Connection: " + e.getMessage());
                System.err.println(" [HINT] If credentials do not match, please check 'spring.datasource.password' in 'src/main/resources/application.properties'.");
            }
            System.out.println("==================================================================");
            System.out.println("   🚀 Web Dashboard: http://localhost:8080");
            System.out.println("   🔌 REST API:      http://localhost:8080/api/stats");
            System.out.println("==================================================================");
        };
    }
}
