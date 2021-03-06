package com.neikeq.kicksemu.game.rooms.match;

import com.neikeq.kicksemu.config.Configuration;
import com.neikeq.kicksemu.game.characters.CharacterManager;
import com.neikeq.kicksemu.game.characters.PlayerInfo;
import com.neikeq.kicksemu.game.characters.LevelCache;
import com.neikeq.kicksemu.game.characters.types.PlayerHistory;
import com.neikeq.kicksemu.game.inventory.products.Item;
import com.neikeq.kicksemu.game.rooms.Room;
import com.neikeq.kicksemu.game.rooms.enums.VictoryResult;
import com.neikeq.kicksemu.game.sessions.Session;
import com.neikeq.kicksemu.network.packets.out.MessageBuilder;
import com.neikeq.kicksemu.network.packets.out.ServerMessage;
import com.neikeq.kicksemu.storage.ConnectionRef;
import com.neikeq.kicksemu.utils.DateUtils;
import com.neikeq.kicksemu.game.events.GameEvents;
import com.neikeq.kicksemu.utils.ThreadUtils;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.apache.commons.lang3.mutable.MutableInt;

import java.util.Optional;

public class MatchResultHandler {

    private static final int COUNTDOWN_DIFF_LIMIT = 20;
    static final int LEVEL_GAP_LIMIT = 10;

    private final Room room;
    private final MatchResult result;
    private final ConnectionRef connection;
    private final LevelCache levelCache = new LevelCache();
    private final MutableInt roomAverageLevel = new MutableInt();

    private final long resultTime = DateUtils.currentTimeMillis();
    private final boolean goldenTime = GameEvents.isGoldenTime();
    private final int roomLevelGap;

    public void handleResult() {
        checkCountdownValidity();
        calculateAverageLevel();
        applyRewards();
        
        waitFixedDelayIfNeeded();
        broadcastMatchResults();
        broadcastResultToObserverPlayers();
        doAfterResultUpdates();
    }

    void applyRewards() {
        getResult().getPlayers().forEach(playerResult -> {
            PlayerRewards playerRewards = new PlayerRewards(this, playerResult);
            playerRewards.applyMatchRewards();
        });
    }

    /**
     * This fixed delay is necessary to avoid golden goal bug.
     * It gives client enough time to exit the repeat camera.
     */
    private void waitFixedDelayIfNeeded() {
        // If the match was not finished manually, or was finished during golden goal
        if (getResult().getCountdown() <= 0) {
            final long delay = DateUtils.currentTimeMillis() - resultTime;
            final int minDelay = 1000;

            if (delay < minDelay) {
                ThreadUtils.sleep(minDelay - delay);
            }
        }
    }

    private void broadcastMatchResults() {
        getResult().getPlayers().forEach(playerResult -> {
            ServerMessage resultMessage = MessageBuilder.matchResult(getResult(),
                    playerResult, getRoom(), getConnection());
            getRoom().getPlayer(playerResult.getPlayerId()).sendAndFlush(resultMessage);
        });
    }

    private void broadcastResultToObserverPlayers() {
        if (!getRoom().getObservers().isEmpty()) {
            // Observer players do not count in stats, so we pass an empty PlayerResult instance
            ServerMessage observerMessage = MessageBuilder.matchResult(getResult(),
                    new PlayerResult(), getRoom(), getConnection());

            getRoom().getObservers().stream().forEach(o ->
                    getRoom().getPlayer(o).sendAndFlush(observerMessage));
        }
    }

    private void doAfterResultUpdates() {
        getResult().getPlayers().forEach(playerResult -> {
            int playerId = playerResult.getPlayerId();
            Session session = room.getPlayer(playerId);

            // If match was not in training mode
            if (getRoom().trainingFactorAllowsRewards()) {
                updatePlayerHistory(playerResult);

                if (playerResult.hasReward()) {
                    MutableBoolean mustNotifyExpiration = new MutableBoolean(false);

                    // Decrease by 1 the remain usages of usage based items
                    session.getCache().getItems(connection).values().stream()
                            .filter(Item::isSelectedUsageItem)
                            .forEach(item -> {
                                item.sumUsages((short) -1);

                                // Update the item in the database
                                PlayerInfo.setInventoryItem(item, playerId, getConnection());

                                // If the item expired
                                if (item.getUsages() <= 0) {
                                    mustNotifyExpiration.setTrue();
                                }
                            });

                    if (mustNotifyExpiration.isTrue()) {
                        CharacterManager.sendItemList(getRoom().getPlayer(playerId));
                    }
                }
            }
        });
    }

    private void updatePlayerHistory(PlayerResult playerResult) {
        int playerId = playerResult.getPlayerId();

        PlayerHistory matchHistory = new PlayerHistory();
        matchHistory.sumMatches(1);

        getPlayerTeamResult(playerId).ifPresent(teamResult -> {
            VictoryResult victoryResult = teamResult.getResult();

            if (victoryResult == VictoryResult.DRAW) {
                matchHistory.sumDraws(1);
            } else if (victoryResult == VictoryResult.WIN) {
                matchHistory.sumWins(1);
            }
        });

        if (playerId == getResult().getMom()) {
            matchHistory.sumMom(1);
        }

        matchHistory.sumValidGoals(playerResult.getGoals());
        matchHistory.sumValidAssists(playerResult.getAssists());
        matchHistory.sumValidInterception(playerResult.getBlocks());
        matchHistory.sumValidShooting(playerResult.getShots());
        matchHistory.sumValidStealing(playerResult.getSteals());
        matchHistory.sumValidTackling(playerResult.getTackles());
        matchHistory.sumTotalPoints(playerResult.getVotePoints());

        PlayerInfo.sumHistory(matchHistory, playerId, getConnection());
    }

    private void checkCountdownValidity() {
        final long elapsedSeconds = (resultTime - getRoom().getTimeStart()) / 1000;
        final long estimatedRealCountdown = 300 - elapsedSeconds;

        // Disable rewards and history updating if the countdown received is not valid
        if ((getResult().getCountdown() < estimatedRealCountdown) &&
                ((getResult().getCountdown() - estimatedRealCountdown) > COUNTDOWN_DIFF_LIMIT)) {
            getRoom().resetTrainingFactor();
        }
    }

    private void calculateAverageLevel() {
        if (isLowersBonusEnabled() && !getResult().getPlayers().isEmpty()) {
            MutableInt averageLevel = new MutableInt(0);

            getResult().getPlayers().forEach(playerResult -> {
                int playerId = playerResult.getPlayerId();
                averageLevel.add(getLevelCache().getPlayerLevel(playerId, getConnection()));
            });

            getRoomAverageLevel().add(averageLevel.getValue() / getResult().getPlayers().size());
        }
    }

    public MatchResultHandler(Room room, MatchResult result, ConnectionRef connection) {
        this.room = room;
        this.result = result;
        this.connection = connection;
        roomLevelGap = room.getMaxLevel() - room.getMinLevel();
    }

    public boolean isLowersBonusEnabled() {
        return Configuration.getBoolean("game.match.bonus.lowers");
    }

    public Optional<TeamResult> getPlayerTeamResult(int playerId) {
        return result.getTeamResult(getRoom().getPlayerTeam(playerId));
    }

    public Optional<TeamResult> getRivalTeamResult(int playerId) {
        return result.getTeamResult(getRoom().getPlayerRivalTeam(playerId));
    }

    public Room getRoom() {
        return room;
    }

    public MatchResult getResult() {
        return result;
    }

    public ConnectionRef getConnection() {
        return connection;
    }

    public LevelCache getLevelCache() {
        return levelCache;
    }

    public MutableInt getRoomAverageLevel() {
        return roomAverageLevel;
    }

    public int getRoomLevelGap() {
        return roomLevelGap;
    }

    public boolean isGoldenTime() {
        return goldenTime;
    }
}
