package SmartTanker;

import jdk.nashorn.internal.runtime.regexp.joni.exception.ValueException;
import uk.ac.nott.cs.g54dia.library.*;

import java.util.Random;

/**
 * Created by JasonChen on 2/12/15.
 */

public class SmartTanker extends Tanker {
    final static int DURATION = 10 * 10000;
    final static int
            // action mode
            REFUEL = 0,
            LOAD_WATER = 1,
            DELIVER_WATER = 2,
            // Driving mode
            EXPLORE = 3,
            DRIVE_TO_PUMP = 4,
            DRIVE_TO_FACILITY = 5;
    int EXPLIM = MAX_FUEL / 2 - VIEW_RANGE - 1;
    final MemPoint FUEL_PUMP = new MemPoint(0, 0);

    int mode = EXPLORE;
    int mode_prev = -1;

    boolean enough_fuel = false;

    MemMap map = new MemMap();
    TaskSys ts = new TaskSys();
    Driver driver = new Driver(map, ts);
    Cell current_cell;
    Task t;

    MemPoint current_point = driver.getCurrentPoint();
    MemPoint target_point = (MemPoint) FUEL_PUMP.clone();

    int explore_direction = -1;
    int explore_direction_prev = explore_direction;
    int water_level = 0;
    int fuel_level = 0;
    Boolean task_list_empty = true;
    long time_left = DURATION;

    public SmartTanker() {}

    @Override
    public Action senseAndAct(Cell[][] view, long time_step) {
        Action act;
        recordMap(view);
        updateState(view, time_step);

        this.mode = arbitrator();

        switch (this.mode) {
            case EXPLORE:
                exploreWorld();
            case DRIVE_TO_FACILITY:
                act = this.driver.driveTo(this.target_point);
                break;
            case DRIVE_TO_PUMP:
                act = this.driver.driveTo(this.FUEL_PUMP);
                break;
            case REFUEL:
                act = new RefuelAction();
                break;
            case LOAD_WATER:
                act = new LoadWaterAction();
                break;
            case DELIVER_WATER:
                act = new DeliverWaterAction(this.t);
                break;
            default:
                throw new ValueException("Unrecognised mode");
        }
        this.mode_prev = this.mode;
        return act;
    }

    private void updateState(Cell[][] view, long time_step) {
        this.time_left = DURATION - time_step;

        this.current_point = this.driver.getCurrentPoint();
        this.current_cell = this.getCurrentCell(view);

        this.water_level = this.getWaterLevel();
        this.fuel_level = this.getFuelLevel();

        this.enough_fuel = checkFuel();

        this.task_list_empty = this.ts.scanTaskList();
    }

    private boolean checkFuel() {
        // add extra one fuel for deliver water or refill water
        int cost = this.current_point.calcDistance(this.target_point) +
                this.target_point.calcDistance(FUEL_PUMP) + 1;

        return cost < this.fuel_level;
    }

    private void recordMap(Cell[][] view) {
        for (int y=-VIEW_RANGE; y < VIEW_RANGE; y++) {
            for (int x=-VIEW_RANGE; x < VIEW_RANGE; x++) {
                int real_x = this.current_point.x + x;
                int real_y = this.current_point.y - y;
                MemPoint point = new MemPoint(real_x, real_y);
                Cell cell = view[VIEW_RANGE + x][VIEW_RANGE + y];
                if (cell instanceof Station) {
                    this.map.appendStation(point, (Station) cell);
                    this.ts.appendTask(point, this.map);
                } else if (cell instanceof Well) {
                    this.map.appendWell(point, (Well) cell);
                }
            }
        }
    }

    private void exploreWorld() {
        /*
        move alone this routine,
        -------------
        start from current point,
        random choose the direction
        */
        Random generator = new Random();

        // start a new explore
        if (this.explore_direction == -1 ||
                this.current_point.calcDistance(FUEL_PUMP) > EXPLIM) {
            do {
                this.explore_direction = generator.nextInt(8);
            } while (this.explore_direction == this.explore_direction_prev);
            this.explore_direction_prev = this.explore_direction;
        } else {
            this.explore_direction = this.explore_direction_prev;
        }
        this.target_point = (MemPoint) this.current_point.clone();
        this.target_point.moveTo(this.explore_direction);
    }

    private int arbitrator() {
        // at fuel pump, gas not max
        if (this.current_cell instanceof FuelPump &&
                this.mode_prev != REFUEL &&
                this.fuel_level < MAX_FUEL) {
            return REFUEL;
        } else
        // at water well, water not max
        if (this.current_cell instanceof Well &&
                this.water_level < MAX_WATER) {
            return LOAD_WATER;
        }

        // not enough fuel to go back and refill
        if (!this.enough_fuel &&
                !(this.current_cell instanceof FuelPump)) {
            // remain fuel can only go back fuel pump directly and current not at fuel pump
            return DRIVE_TO_PUMP;
        }

        // task list not empty
        if (!this.task_list_empty) {
            TaskPair task_pair = this.driver.getNextPoint(this.current_point, this.water_level, this.fuel_level, this.time_left);
            this.t = task_pair.getTask();
            MemPoint task_point = task_pair.getTaskPoint();
            MemPoint well_point = task_pair.getWellPoint();

            // found best task to finish
            if (this.t != null && task_point != null) {
                if (well_point != null) {
                    // need well
                    this.target_point = well_point;
                    return DRIVE_TO_FACILITY;
                } else
                    // no need go well
                    if (!this.current_point.equals(task_point)) {
                        // not at task point
                        this.target_point = task_point;
                        return DRIVE_TO_FACILITY;
                    } else {
                        // at task cell
                        return DELIVER_WATER;
                    }
            }
        }

        return EXPLORE;
    }
}
