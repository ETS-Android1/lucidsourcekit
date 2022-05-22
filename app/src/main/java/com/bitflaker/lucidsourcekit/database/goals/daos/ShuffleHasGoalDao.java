package com.bitflaker.lucidsourcekit.database.goals.daos;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.bitflaker.lucidsourcekit.database.goals.entities.ShuffleHasGoal;
import com.bitflaker.lucidsourcekit.database.goals.entities.resulttables.DetailedShuffleHasGoal;
import com.bitflaker.lucidsourcekit.database.goals.entities.resulttables.ShuffleHasGoalStats;
import com.bitflaker.lucidsourcekit.database.goals.entities.resulttables.GoalStats;

import java.util.List;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;

@Dao
public interface ShuffleHasGoalDao {
    @Query("SELECT * FROM ShuffleHasGoal ORDER BY shuffleId DESC LIMIT 1000")
    Single<List<ShuffleHasGoal>> getAll();

    @Query("SELECT * FROM ShuffleHasGoal LEFT JOIN Goal ON ShuffleHasGoal.goalId = Goal.goalId LEFT JOIN Shuffle ON ShuffleHasGoal.shuffleId = Shuffle.shuffleId WHERE Shuffle.dayStartTimestamp = :dayStartTimestamp and Shuffle.dayEndTimestamp = :dayEndTimestamp")
    Single<List<DetailedShuffleHasGoal>> getShuffleFrom(long dayStartTimestamp, long dayEndTimestamp);

    @Query("SELECT COUNT(*) AS goalCount, AVG(Goal.difficulty) AS avgDifficulty, SUM(CASE WHEN ShuffleHasGoal.achieved = 1 then 1 else 0 end) AS achievedCount FROM ShuffleHasGoal LEFT JOIN Goal ON ShuffleHasGoal.goalId = Goal.goalId LEFT JOIN Shuffle ON ShuffleHasGoal.shuffleId = Shuffle.shuffleId WHERE Shuffle.dayStartTimestamp >= :dayStartTimestamp and Shuffle.dayEndTimestamp <= :dayEndTimestamp")
    Single<ShuffleHasGoalStats> getShufflesFromBetween(long dayStartTimestamp, long dayEndTimestamp);

    @Query("SELECT COUNT(goalId) FROM ShuffleHasGoal WHERE goalId IN (:goalIds)")
    Single<List<Integer>> getCountOfGoalsDrawn(List<Integer> goalIds);

    @Query("SELECT COUNT(*) FROM ShuffleHasGoal")
    Single<Integer> getAmountOfTotalDrawnGoals();

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    Completable insertAll(List<ShuffleHasGoal> shuffleHasGoals);

    @Query("DELETE FROM ShuffleHasGoal WHERE shuffleId = :id")
    Completable deleteAllWithShuffleId(int id);

    @Delete
    Completable delete(ShuffleHasGoal shuffleHasGoal);

    @Query("DELETE FROM ShuffleHasGoal")
    Completable deleteAll();

    @Query("UPDATE ShuffleHasGoal SET achieved = :achieved WHERE shuffleId = :shuffleId AND goalId = :goalId")
    void setAchievedState(int shuffleId, int goalId, boolean achieved);

    @Query("SELECT (SELECT COUNT(*) FROM ShuffleHasGoal WHERE goalId = :goalId AND achieved = 1) as achievedCount, (SELECT COUNT(*) FROM ShuffleHasGoal WHERE goalId = :goalId) as totalCount")
    Single<GoalStats> getAchieveStatsOfGoal(int goalId);
}