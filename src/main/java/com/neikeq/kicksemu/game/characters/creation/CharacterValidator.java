package com.neikeq.kicksemu.game.characters.creation;

import com.neikeq.kicksemu.game.characters.types.Animation;
import com.neikeq.kicksemu.game.characters.types.PlayerStats;
import com.neikeq.kicksemu.game.characters.types.Position;
import com.neikeq.kicksemu.game.characters.types.StatsInfo;
import com.neikeq.kicksemu.game.inventory.types.ItemType;
import com.neikeq.kicksemu.game.table.TableManager;
import com.neikeq.kicksemu.game.table.ItemFree;
import com.neikeq.kicksemu.storage.ConnectionRef;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

class CharacterValidator {

    private static final String validSpecialChars = "()[]-_.,:;";

    private static final List<Short> VALID_POSITIONS =
            Arrays.asList(Position.FW, Position.MF, Position.DF);

    private static final List<Integer> VALID_FACES = Arrays.asList(
                    101, 102, 103, 104, 105, 106, 201, 202, 203, 301, 302,
                    303, 304, 401, 402, 403, 404, 405, 406, 407, 408, 409
            );

    private static final int NAME_MIN_LENGTH = 3;
    private static final int NAME_MAX_LENGTH = 14;

    private static final int STATS_GLOBAL = 490;
    private static final int MIN_STATS_POINTS = 0;
    private static final int MAX_STATS_POINTS = 10;

    public static short validate(CharacterBase character) {
        short result = CreationResult.SUCCESS;

        if (!isValidName(character.getName()) || nameAlreadyInUse(character.getName())) {
            result = CreationResult.NAME_ALREADY_IN_USE;
        } else if (!containsValidStats(character) || !isValidPosition(character.getPosition())) {
            result = CreationResult.INVALID_CHARACTER;
        } else if (!containsValidItems(character) || !isValidFace(character) ||
                !isValidAnimation(character)) {
            result = CreationResult.SYSTEM_PROBLEM;
        }

        return result;
    }

    private static boolean isValidPosition(short position) {
        return VALID_POSITIONS.contains(position);
    }

    private static boolean isValidName(String name) {
        return (name.length() <= NAME_MAX_LENGTH) && (name.length() >= NAME_MIN_LENGTH) &&
                !nameContainsInvalidChar(name);
    }

    private static boolean nameContainsInvalidChar(String name) {
        for (char current : name.toLowerCase().toCharArray()) {
            if (!Character.isAlphabetic(current) && !Character.isDigit(current) &&
                    !validSpecialChars.contains(String.valueOf(current))) {
                return true;
            }
        }

        return false;
    }

    private static boolean nameAlreadyInUse(String name) {
        final String query = "SELECT count(1) FROM characters WHERE name = ?";

        try (ConnectionRef connection = ConnectionRef.ref();
             PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, name);

            try (ResultSet result = stmt.executeQuery()) {
                return result.next() && (result.getInt(1) > 0);
            }

        } catch (SQLException e) {
            return true;
        }
    }

    private static boolean containsValidStats(CharacterBase character) {
        PlayerStats stats = StatsInfo.getInstance().getCreationStats()
                .get(character.getPosition());

        return (character.getTotalStats() == STATS_GLOBAL) &&
                (character.getStatsPoints() <= MAX_STATS_POINTS) &&
                (character.getStatsPoints() >= MIN_STATS_POINTS) &&
                (character.getStats().getRunning() >= stats.getRunning()) &&
                (character.getStats().getEndurance() >= stats.getEndurance()) &&
                (character.getStats().getAgility() >= stats.getAgility()) &&
                (character.getStats().getBallControl() >= stats.getBallControl()) &&
                (character.getStats().getDribbling() >= stats.getDribbling()) &&
                (character.getStats().getStealing() >= stats.getStealing()) &&
                (character.getStats().getTackling() >= stats.getTackling()) &&
                (character.getStats().getHeading() >= stats.getHeading()) &&
                (character.getStats().getShortShots() >= stats.getShortShots()) &&
                (character.getStats().getLongShots() >= stats.getLongShots()) &&
                (character.getStats().getCrossing() >= stats.getCrossing()) &&
                (character.getStats().getShortPasses() >= stats.getShortPasses()) &&
                (character.getStats().getLongPasses() >= stats.getLongPasses()) &&
                (character.getStats().getMarking() >= stats.getMarking()) &&
                (character.getStats().getGoalkeeping() >= stats.getGoalkeeping()) &&
                (character.getStats().getPunching() >= stats.getPunching()) &&
                (character.getStats().getDefense() >= stats.getDefense());
    }

    private static boolean containsValidItems(CharacterBase cBase) {
        Optional<ItemFree> head = TableManager.getItemFree(fi ->
                (fi.getId() == cBase.getDefaultHead()) && (fi.getType() == ItemType.HEAD));
        Optional<ItemFree> shirt = TableManager.getItemFree(fi ->
                (fi.getId() == cBase.getDefaultShirts()) && (fi.getType() == ItemType.SHIRTS));
        Optional<ItemFree> pant = TableManager.getItemFree(fi ->
                (fi.getId() == cBase.getDefaultPants()) && (fi.getType() == ItemType.PANTS));
        Optional<ItemFree> shoes = TableManager.getItemFree(fi ->
                (fi.getId() == cBase.getDefaultShoes()) && (fi.getType() == ItemType.SHOES));

        return head.isPresent() && shirt.isPresent() && pant.isPresent() && shoes.isPresent();
    }

    private static boolean isValidFace(CharacterBase character) {
        return VALID_FACES.contains((int) character.getFace());
    }

    private static boolean isValidAnimation(CharacterBase character) {
        return (character.getAnimation() == Animation.MALE) ?
                (character.getFace() < 400) :
                ((character.getAnimation() == Animation.FEMALE) && (character.getFace() > 400));
    }
}
