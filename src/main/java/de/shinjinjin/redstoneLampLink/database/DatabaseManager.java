package de.shinjinjin.redstoneLampLink.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;


public class DatabaseManager {
    private Connection connection;

    public void connect() throws SQLException {
        // SQLite-Datenbankverbindung (du kannst den Pfad anpassen)
        connection = DriverManager.getConnection("jdbc:sqlite:plugins/RedstoneLampLink/lampcontrol.db");
    }

    public Connection getConnection() {
        return connection;
    }

    public void disconnect() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    public void setupTables() throws SQLException {
        String createLampTable = "CREATE TABLE IF NOT EXISTS Lamp (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "name TEXT, " +
                "world TEXT, " +
                "x INTEGER, " +
                "y INTEGER, " +
                "z INTEGER)";
        String createButtonTable = "CREATE TABLE IF NOT EXISTS Button (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "world TEXT, " +
                "x INTEGER, " +
                "y INTEGER, " +
                "z INTEGER)";
        String createLinkTable = "CREATE TABLE IF NOT EXISTS LampButtonLink (" +
                "lamp_id INTEGER, " +
                "button_id INTEGER, " +
                "FOREIGN KEY (lamp_id) REFERENCES Lamp(id), " +
                "FOREIGN KEY (button_id) REFERENCES Button(id))";
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createLampTable);
            stmt.execute(createButtonTable);
            stmt.execute(createLinkTable);
        }
    }
}
