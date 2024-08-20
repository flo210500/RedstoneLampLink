package de.shinjinjin.redstoneLampLink;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Lightable;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.BlockVector;
import de.shinjinjin.redstoneLampLink.database.DatabaseManager;
import de.shinjinjin.redstoneLampLink.database.LampDAO;
import org.bukkit.block.data.BlockData;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;

import java.sql.SQLException;
import java.util.*;
import java.io.File;

public class LampControlPlugin extends JavaPlugin implements Listener {
    private DatabaseManager databaseManager;
    private LampDAO lampDAO;

    // Map to store linked buttons and lamp names
    private Map<BlockVector, List<String>> buttonToLampsMap = new HashMap<>();
    // Map to store lamp positions and their names
    private Map<String, List<BlockVector>> lampNameToLocationsMap = new HashMap<>();

    @Override
    public void onEnable() {
        databaseManager = new DatabaseManager();
        try {

            File dataFolder = getDataFolder();
            if (!dataFolder.exists()) {
                dataFolder.mkdirs();
            }


            // Erstellen Sie den Pfad zur Datenbankdatei
            String dbPath = new File(dataFolder, "lampcontrol.db").getAbsolutePath();
            databaseManager.connect();
            databaseManager.setupTables();
            lampDAO = new LampDAO(databaseManager.getConnection());

            // Load lamp and button data from the database
            loadLampAndButtonData();

            getLogger().info("Datenbankverbindung erfolgreich hergestellt und Daten geladen.");
        } catch (Exception e) {
            getLogger().severe("Fehler beim Herstellen der Datenbankverbindung oder Laden der Daten: " + e.getMessage());
        }

        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        databaseManager.disconnect();
        getLogger().info("Datenbankverbindung geschlossen.");
    }

    private String toString(BlockVector vector) {
        return vector.getBlockX() + "," + vector.getBlockY() + "," + vector.getBlockZ();
    }

    private BlockVector fromString(String str) {
        String[] parts = str.split(",");
        int x = Integer.parseInt(parts[0]);
        int y = Integer.parseInt(parts[1]);
        int z = Integer.parseInt(parts[2]);
        return new BlockVector(x, y, z);
    }

    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (!(sender instanceof Player)) {
            return completions;
        }

        Player player = (Player) sender;

        // Hauptbefehl 'lamplink'
        if (command.getName().equalsIgnoreCase("lamplink")) {
            // Wenn keine Argumente angegeben sind, zeige alle Subcommands an
            if (args.length == 1) {
                if (player.hasPermission("lampcontrol.create")) {
                    completions.add("linklamp");
                }
                if (player.hasPermission("lampcontrol.link")) {
                    completions.add("linkbutton");
                }
                if (player.hasPermission("lampcontrol.reload")) {
                    completions.add("reloadmapping");
                }
                if (player.hasPermission("lampcontrol.list")) {
                    completions.add("list");
                }
            }
            // Wenn ein Subcommand eingegeben ist, füge passende Argumente hinzu
            else if (args.length == 2) {
                String subcommand = args[0].toLowerCase();

                // Vervollständigung für linklamp und linkbutton: Liste der Lampennamen
                if (subcommand.equals("linklamp") || subcommand.equals("linkbutton")) {
                    if (player.hasPermission("lampcontrol.create") || player.hasPermission("lampcontrol.link")) {
                        completions.addAll(getLampNames());
                    }
                }

                // Vervollständigung für list: Filtere nach Lampennamen
                else if (subcommand.equals("list")) {
                    if (player.hasPermission("lampcontrol.list")) {
                        completions.addAll(getLampNames());
                    }
                }
            }
        }

        return completions;
    }

    private List<String> getLampNames() {
        return new ArrayList<>(lampNameToLocationsMap.keySet());
    }

    private boolean handleLinkLampCommand(Player player, String[] args) {
        if (!player.hasPermission("lampcontrol.create")) {
            player.sendMessage(ChatColor.RED + "You don't have permission to create linked lamps.");
            return true;
        }

        if (args.length < 2) {
            player.sendMessage(ChatColor.GREEN + "Usage: /lamplink linklamp <lamp_name>");
            return true;
        }

        String lampName = args[1];
        Block targetBlock = player.getTargetBlock(null, 5);

        if (targetBlock.getType() == Material.REDSTONE_LAMP || isBulb(targetBlock)) {
            BlockVector lampLocation = targetBlock.getLocation().toVector().toBlockVector();
            String worldName = targetBlock.getWorld().getName();

            try {
                int lampId = lampDAO.addLamp(lampName, worldName, lampLocation.getBlockX(), lampLocation.getBlockY(), lampLocation.getBlockZ());
                player.sendMessage("Lamp linked with name: " + ChatColor.DARK_AQUA + lampName + ChatColor.WHITE +". Now use /lamplink linkbutton <lamp_name> to link it with a button.");
                reloadMappings(player);
            } catch (SQLException e) {
                player.sendMessage("Failed to link lamp: "+ ChatColor.DARK_RED + e.getMessage());
                getLogger().severe("Error linking lamp: " + e.getMessage());
            }
        } else {
            player.sendMessage(ChatColor.RED + "You must look at a Redstone Lamp or a Copper Bulb to link it.");
        }
        return true;
    }

    private boolean handleLinkButtonCommand(Player player, String[] args) {
        if (!player.hasPermission("lampcontrol.link")) {
            player.sendMessage(ChatColor.RED + "You don't have permission to link buttons.");
            return true;
        }

        if (args.length < 2) {
            player.sendMessage(ChatColor.GREEN + "Usage: /lamplink linkbutton <lamp_name>");
            return true;
        }

        String lampName = args[1];
        Block targetBlock = player.getTargetBlock(null, 5);

        if (isButton(targetBlock)) {
            BlockVector buttonLocation = targetBlock.getLocation().toVector().toBlockVector();
            String worldName = targetBlock.getWorld().getName();

            try {
                int buttonId = lampDAO.addButton(worldName, buttonLocation.getBlockX(), buttonLocation.getBlockY(), buttonLocation.getBlockZ());
                int lampId = lampDAO.getLampIdByName(lampName);

                if (lampId != -1) {
                    lampDAO.linkLampToButton(lampId, buttonId);
                    buttonToLampsMap.computeIfAbsent(buttonLocation, k -> new ArrayList<>()).add(lampName);
                    lampNameToLocationsMap.computeIfAbsent(lampName, k -> new ArrayList<>()).add(buttonLocation);

                    player.sendMessage("Button linked to lamp " + ChatColor.DARK_AQUA + lampName + ".");
                    getLogger().info("Button linked to lamp " + lampName + " successfully.");
                    reloadMappings(player);
                } else {
                    player.sendMessage("Lamp with name "+ ChatColor.RED + lampName + " not found.");
                }
            } catch (SQLException e) {
                player.sendMessage("Failed to link button: " + ChatColor.DARK_RED + e.getMessage());
                getLogger().severe("Error linking button: " + e.getMessage());
            }
        } else {
            player.sendMessage(ChatColor.RED + "You must look at a button to link it.");
        }
        return true;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command.");
            return true;
        }

        Player player = (Player) sender;

        if (command.getName().equalsIgnoreCase("lamplink")) {
            if (args.length < 1) {
                player.sendMessage( ChatColor.GREEN + "Usage: /lamplink <linklamp|linkbutton|reloadmapping|list>");
                return true;
            }

            String subcommand = args[0].toLowerCase();
            switch (subcommand) {
                case "linklamp":
                    return handleLinkLampCommand(player, args);
                case "linkbutton":
                    return handleLinkButtonCommand(player, args);
                case "reloadmapping":
                    if (player.hasPermission("lampcontrol.reload")) {
                        reloadMappings(player);
                        return true;
                    } else {
                        player.sendMessage(ChatColor.RED + "You don't have permission to reload mappings.");
                        return true;
                    }
                case "list":
                    if (player.hasPermission("lampcontrol.list")) {
                        if (args.length == 2) {
                            listLinksForLampName(sender, args[1]);
                        } else {
                            listLinkNames(sender);
                        }
                        return true;
                    } else {
                        player.sendMessage(ChatColor.RED + "You don't have permission to list links.");
                        return true;
                    }
                case "help":
                    player.sendMessage(ChatColor.GREEN + "Usage: /lamplink <linklamp|linkbutton|reloadmapping|list>");
                    return true;
                default:
                    player.sendMessage(ChatColor.GREEN + "Unknown subcommand. Usage: /lamplink <linklamp|linkbutton|reloadmapping|list> [args...]");
                    return true;
            }
        }

        return false;
    }
    private void listLinkNames(CommandSender sender) {
        if (lampNameToLocationsMap.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "No lamp names found.");
            return;
        }

        StringBuilder message = new StringBuilder("Available Lamp Names:\n");
        for (String lampName : lampNameToLocationsMap.keySet()) {
            message.append(String.format(" - %s\n", lampName));
        }
        sender.sendMessage(message.toString());
    }

    private void listLinksForLampName(CommandSender sender, String lampName) {
        List<BlockVector> lampLocations = lampNameToLocationsMap.get(lampName);
        if (lampLocations == null || lampLocations.isEmpty()) {
            sender.sendMessage("No lamps found with the name: "+ ChatColor.DARK_AQUA + lampName);
            return;
        }

        TextComponent message = new TextComponent("Lamp Details for '"+ ChatColor.DARK_AQUA + lampName + "':\n");

        // Sammeln aller Buttons für den Lampennamen
        Set<BlockVector> buttonLocationsSet = new HashSet<>();
        for (BlockVector lampLocation : lampLocations) {
            List<BlockVector> buttonLocations = getButtonsForLampName(lampName);
            if (buttonLocations != null && !buttonLocations.isEmpty()) {
                buttonLocationsSet.addAll(buttonLocations);
            }
        }

        // Hinzufügen der Lampeninformationen
        for (BlockVector lampLocation : lampLocations) {
            TextComponent lampComponent = new TextComponent(String.format(" - Lamp at %s\n", lampLocation.toString()));
            lampComponent.setColor(net.md_5.bungee.api.ChatColor.AQUA); // Dark Aqua color
            lampComponent.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new TextComponent[]{
                    new TextComponent("Click to teleport to lamp location")
            }));
            lampComponent.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tp " + lampLocation.getBlockX() + " " + lampLocation.getBlockY() + " " + lampLocation.getBlockZ()));
            message.addExtra(lampComponent);
        }

        // Hinzufügen der Button-Informationen, nur einmal pro Lampenname
        if (!buttonLocationsSet.isEmpty()) {
            TextComponent buttonHeader = new TextComponent("   Linked Buttons:\n");
            buttonHeader.setColor(net.md_5.bungee.api.ChatColor.DARK_AQUA); // Dark Aqua color for header
            message.addExtra(buttonHeader);

            for (BlockVector buttonLocation : buttonLocationsSet) {
                TextComponent buttonComponent = new TextComponent(String.format("    * Button at %s\n", buttonLocation.toString()));
                buttonComponent.setColor(net.md_5.bungee.api.ChatColor.GREEN); // Different color for button locations
                buttonComponent.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new TextComponent[]{
                        new TextComponent("Click to teleport to button location")
                }));
                buttonComponent.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tp " + buttonLocation.getBlockX() + " " + buttonLocation.getBlockY() + " " + buttonLocation.getBlockZ()));
                message.addExtra(buttonComponent);
            }
        } else {
            TextComponent noButtons = new TextComponent("   No linked buttons.\n");
            noButtons.setColor(net.md_5.bungee.api.ChatColor.RED); // Color for no buttons found
            message.addExtra(noButtons);
        }

        sender.spigot().sendMessage(message);
    }

    private List<BlockVector> getButtonsForLampName(String lampName) {
        List<BlockVector> buttonLocations = new ArrayList<>();
        for (Map.Entry<BlockVector, List<String>> entry : buttonToLampsMap.entrySet()) {
            if (entry.getValue().contains(lampName)) {
                buttonLocations.add(entry.getKey());
            }
        }
        return buttonLocations;
    }

    private void reloadMappings(CommandSender sender) {
        try {
            // Load lamp and button data from the database
            loadLampAndButtonData();
            sender.sendMessage(ChatColor.GREEN + "Lamp and button mappings have been reloaded successfully.");
            getLogger().info("Lamp and button mappings reloaded successfully.");
        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + "Failed to reload lamp and button mappings: " + e.getMessage());
            getLogger().severe("Error reloading lamp and button mappings: " + e.getMessage());
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (isButton(block)) {  // Verwende die neue Methode
            BlockVector buttonLocation = block.getLocation().toVector().toBlockVector();
            List<String> lampNames = buttonToLampsMap.get(buttonLocation);

            if (lampNames != null) {
                for (String lampName : lampNames) {
                    List<BlockVector> lampLocations = lampNameToLocationsMap.get(lampName);

                    if (lampLocations != null) {
                        for (BlockVector lampLocation : lampLocations) {
                            Block lampBlock = block.getWorld().getBlockAt(lampLocation.getBlockX(), lampLocation.getBlockY(), lampLocation.getBlockZ());
                            if (lampBlock.getType() == Material.REDSTONE_LAMP || isBulb(lampBlock)) {
                                BlockData blockData = lampBlock.getBlockData();
                                if (blockData instanceof Lightable) {
                                    Lightable lightable = (Lightable) blockData;
                                    boolean newState = !lightable.isLit();
                                    lightable.setLit(newState);
                                    lampBlock.setBlockData(lightable);
                                    lampBlock.getState().update(true, true); // Aktualisiert den Blockstatus
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) throws SQLException {
        Block block = event.getBlock();
        BlockVector blockLocation = block.getLocation().toVector().toBlockVector();

        // Check if the block is a button
        if (isButton(block)) {
            // Check if the button is linked to any lamps
            List<String> linkedLampNames = buttonToLampsMap.get(blockLocation);

            if (linkedLampNames != null && !linkedLampNames.isEmpty()) {
                if (!event.getPlayer().hasPermission("lampcontrol.remove")) {
                    event.setCancelled(true);
                    event.getPlayer().sendMessage(ChatColor.RED + "You don't have permission to remove this!");
                } else {
                    // Remove the button group
                    int buttonId = lampDAO.getButtonId(blockLocation);
                    removeButtonGroup(blockLocation);
                    lampDAO.removeButton(buttonId); // Entferne den Button aus der Datenbank
                    event.getPlayer().sendMessage(ChatColor.GREEN + "Linked button removed.");
                }
            } else {
                // If the button is not linked, allow breaking
                return;
            }
        } else if (block.getType() == Material.REDSTONE_LAMP || isBulb(block)) {
            boolean lampRemoved = false;

            if (event.getPlayer().hasPermission("lampcontrol.remove")) {
                for (Map.Entry<String, List<BlockVector>> entry : lampNameToLocationsMap.entrySet()) {
                    List<BlockVector> locations = entry.getValue();
                    if (locations.remove(blockLocation)) {
                        lampRemoved = true;
                        if (locations.isEmpty()) {
                            lampNameToLocationsMap.remove(entry.getKey());
                        }
                        try {
                            int lampId = lampDAO.getLampId(entry.getKey());
                            lampDAO.removeLamp(lampId); // Entferne die Lampe aus der Datenbank
                        } catch (SQLException e) {
                            getLogger().severe("Failed to remove lamp from database: " + e.getMessage());
                            event.getPlayer().sendMessage(ChatColor.RED + "Failed to remove lamp from database.");
                        }
                        break;
                    }
                }

                if (lampRemoved) {
                    event.getPlayer().sendMessage(ChatColor.GREEN + "Linked lamp removed.");
                } else {
                    event.getPlayer().sendMessage(ChatColor.AQUA + "Lamp was not part of any group.");
                }
            } else {
                event.setCancelled(true);
                event.getPlayer().sendMessage(ChatColor.RED + "You don't have permission to remove this lamp!");
            }
        }
    }

    private void removeLampGroup(String lampName, BlockVector lampLocation) {
        List<BlockVector> locations = lampNameToLocationsMap.get(lampName);

        if (locations != null) {
            locations.remove(lampLocation);
            if (locations.isEmpty()) {
                lampNameToLocationsMap.remove(lampName);
            }
        }
    }

    private void removeButtonGroup(BlockVector buttonLocation) {
        List<String> linkedLampNames = buttonToLampsMap.get(buttonLocation);

        if (linkedLampNames != null) {
            for (String lampName : linkedLampNames) {
                List<BlockVector> lampLocations = lampNameToLocationsMap.get(lampName);
                if (lampLocations != null) {
                    // Remove the button reference from each lamp location
                    lampLocations.removeIf(loc -> loc.equals(buttonLocation));
                    if (lampLocations.isEmpty()) {
                        lampNameToLocationsMap.remove(lampName);
                    }
                }
            }
            buttonToLampsMap.remove(buttonLocation);
        }
    }

    private void loadLampAndButtonData() {
        try {
            // Load lamp and button data from the database
            Map<BlockVector, List<String>> buttonToLampsMapFromDB = lampDAO.loadButtonToLampsMap();
            Map<String, List<BlockVector>> lampNameToLocationsMapFromDB = lampDAO.loadLampNameToLocationsMap();

            // Debug-Ausgabe
            getLogger().info("Loaded button to lamps map: " + buttonToLampsMapFromDB.toString());
            getLogger().info("Loaded lamp name to locations map: " + lampNameToLocationsMapFromDB.toString());

            // Set the maps to the loaded data
            buttonToLampsMap.clear();
            buttonToLampsMap.putAll(buttonToLampsMapFromDB);

            lampNameToLocationsMap.clear();
            lampNameToLocationsMap.putAll(lampNameToLocationsMapFromDB);

            getLogger().info("Lamp and Button data loaded successfully.");
        } catch (SQLException e) {
            getLogger().severe("Error loading lamp and button data from the database: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private boolean isButton(Block block) {
        // Prüft, ob der Block ein beliebiger Button-Typ ist
        return block != null && (block.getType().name().endsWith("_BUTTON") || block.getType().name().endsWith("_PLATE"));
    }

    private boolean isBulb(Block block) {
        // Prüft, ob der Block ein beliebiger Button-Typ ist
        return block != null && (block.getType().name().endsWith("_BULB"));
    }

}
