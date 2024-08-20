package de.shinjinjin.redstoneLampLink.database;

import org.bukkit.util.BlockVector;
import java.sql.*;
import java.util.*;
import de.shinjinjin.redstoneLampLink.Lamp;

public class LampDAO {
    private final Connection connection;

    public LampDAO(Connection connection) {
        this.connection = connection;
    }

    public int addLamp(String name, String world, int x, int y, int z) throws SQLException {
        String sql = "INSERT INTO Lamp (name, world, x, y, z) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, name);
            stmt.setString(2, world);
            stmt.setInt(3, x);
            stmt.setInt(4, y);
            stmt.setInt(5, z);
            stmt.executeUpdate();

            try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    return generatedKeys.getInt(1);
                } else {
                    throw new SQLException("Creating lamp failed, no ID obtained.");
                }
            }
        }
    }
    public int addButton(String world, int x, int y, int z) throws SQLException {
        String sql = "INSERT INTO Button (world, x, y, z) VALUES (?, ?, ?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, world);
            stmt.setInt(2, x);
            stmt.setInt(3, y);
            stmt.setInt(4, z);
            stmt.executeUpdate();

            try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    return generatedKeys.getInt(1);
                } else {
                    throw new SQLException("Creating button failed, no ID obtained.");
                }
            }
        }
    }

    public void linkLampToButton(int lampId, int buttonId) throws SQLException {
        String sql = "INSERT INTO LampButtonLink (lamp_id, button_id) VALUES (?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, lampId);
            stmt.setInt(2, buttonId);
            stmt.executeUpdate();
        }
    }


    public int getLampIdByName(String name) throws SQLException {
        String sql = "SELECT id FROM Lamp WHERE name = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, name);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("id");
                } else {
                    return -1; // Lamp not found
                }
            }
        }
    }

    public Map<BlockVector, List<String>> loadButtonToLampsMap() throws SQLException {
        Map<BlockVector, List<String>> map = new HashMap<>();
        String query = "SELECT Button.x, Button.y, Button.z, Lamp.name " +
                "FROM LampButtonLink " +
                "JOIN Button ON LampButtonLink.button_id = Button.id " +
                "JOIN Lamp ON LampButtonLink.lamp_id = Lamp.id";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {

            while (rs.next()) {
                BlockVector buttonLocation = new BlockVector(
                        rs.getInt("x"),
                        rs.getInt("y"),
                        rs.getInt("z")
                );

                String lampName = rs.getString("name");
                if (!map.containsKey(buttonLocation)) {
                    map.put(buttonLocation, new ArrayList<>());
                }
                map.get(buttonLocation).add(lampName);
            }
        }
        return map;
    }

    public Map<String, List<BlockVector>> loadLampNameToLocationsMap() throws SQLException {
        Map<String, List<BlockVector>> map = new HashMap<>();
        String query = "SELECT name, x, y, z FROM Lamp";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {

            while (rs.next()) {
                String lampName = rs.getString("name");
                BlockVector location = new BlockVector(
                        rs.getInt("x"),
                        rs.getInt("y"),
                        rs.getInt("z")
                );

                map.computeIfAbsent(lampName, k -> new ArrayList<>()).add(location);
            }
        }
        return map;
    }

    public List<Lamp> getLampsByButton(int buttonId) throws SQLException {
        List<Lamp> lamps = new ArrayList<>();
        String sql = "SELECT Lamp.* FROM LampButtonLink " +
                "JOIN Lamp ON Lamp.id = LampButtonLink.lamp_id " +
                "WHERE LampButtonLink.button_id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, buttonId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    lamps.add(new Lamp(
                            rs.getInt("id"),
                            rs.getString("name"),
                            rs.getString("world"),
                            rs.getInt("x"),
                            rs.getInt("y"),
                            rs.getInt("z")
                    ));
                }
            }
        }
        return lamps;
    }

    public void removeButton(int buttonId) throws SQLException {
        String query = "DELETE FROM Button WHERE id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setInt(1, buttonId);
            int rowsAffected = stmt.executeUpdate();

            if (rowsAffected == 0) {
                // Falls keine Zeile gelöscht wurde, werfen wir eine Ausnahme
                throw new SQLException("No button found with ID " + buttonId + " to delete.");
            }
        } catch (SQLException e) {
            // Logge den Fehler und werfe ihn weiter
            throw e; // Weiterwerfen, um den Fehler an den Aufrufer zu kommunizieren
        }
    }

    public void removeLamp(int lampId) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement("DELETE FROM Lamp WHERE id = ?")) {
            stmt.setInt(1, lampId);
            stmt.executeUpdate();
        }
    }

    public void unlinkLampFromButton(int lampId, int buttonId) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement("DELETE FROM LampButtonLink WHERE lamp_id = ? AND button_id = ?")) {
            stmt.setInt(1, lampId);
            stmt.setInt(2, buttonId);
            stmt.executeUpdate();
        }
    }

    public int getButtonId(BlockVector buttonLocation) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement("SELECT id FROM Button WHERE x = ? AND y = ? AND z = ?")) {
            stmt.setInt(1, buttonLocation.getBlockX());
            stmt.setInt(2, buttonLocation.getBlockY());
            stmt.setInt(3, buttonLocation.getBlockZ());
            ResultSet rs = stmt.executeQuery();
            return rs.next() ? rs.getInt("id") : -1;
        }
    }

    public int getLampId(String lampName) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement("SELECT id FROM Lamp WHERE name = ?")) {
            stmt.setString(1, lampName);
            ResultSet rs = stmt.executeQuery();
            return rs.next() ? rs.getInt("id") : -1;
        }
    }

}

