package me.pantre.app.bean.peripheral;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import co.bytetechnology.thm.MainActivity;
import me.pantre.app.peripheral.DragonfruitThingMagicWrapper;
import me.pantre.app.peripheral.GpioContstants;
import me.pantre.app.peripheral.GpioShell;
import me.pantre.app.peripheral.ThingMagicDriver;

public class DragonFruitFacade {

    public final MainActivity mainActivity;
    private final GpioShell gpioShell;

    public DragonFruitFacade(MainActivity mainActivity) {
        this.mainActivity = mainActivity;
        gpioShell = new GpioShell();
    }

    private static final boolean IS_LOGGING_ENABLED = true;

    /**
     * Kit name.
     */
    private static final String KIT_NAME = "Dragonfruit";

    /**
     * ThingMagic antennas count.
     */
    private static final int THING_MAGIC_CHIP_ANTENNAS_COUNT = 2,
            THING_MAGIC_REAL_ANTENNAS_COUNT = 8;

    private ThingMagicDriver thingMagicDriver;

    private ScheduledFuture<?> thingMagicConnectTask;
    private int thingMagicConnectionRetryCounter = 0;
    private boolean initPeripheralsInvoked = false;

    public void initPeripherals() {
        if (initPeripheralsInvoked) {
            // Guard against re-entry
            return;
        }
        System.out.printf("%s initPeripherals() called.", KIT_NAME);
        System.out.println();
        initPeripheralsInvoked = true;

        // Initialize thing magic driver.
        thingMagicDriver = new ThingMagicDriver(this, new DragonfruitThingMagicWrapper(), false,
                THING_MAGIC_CHIP_ANTENNAS_COUNT, THING_MAGIC_REAL_ANTENNAS_COUNT);
        initGpioPins();

        final long thingMagicConnectTimeout = 8000;

        // Initialize ThingMagic.
        final ScheduledExecutorService thingMagicConnectionScheduler = Executors.newSingleThreadScheduledExecutor();
        thingMagicConnectTask = thingMagicConnectionScheduler.scheduleWithFixedDelay(() -> {
            System.out.printf("Connecting ThingMagic. Attempt #%d", ++thingMagicConnectionRetryCounter);
            System.out.println();

            thingMagicDriver.connect(mainActivity);

            System.out.printf("ThingMagic isConnected? %s", thingMagicDriver.isConnected());
            System.out.println();

            if (thingMagicDriver.isConnected()) {
                thingMagicConnectTask.cancel(true);
            }
        }, thingMagicConnectTimeout, thingMagicConnectTimeout, TimeUnit.MILLISECONDS);
    }

    /**
     * Choose a shelf using muxs.
     */
    public void setShelf(final int shelf) {
        if (IS_LOGGING_ENABLED) System.out.printf("Set shelf to %d", shelf);
        System.out.println();

        switch (shelf) {
            case 1:
                gpioShell.setGpioValue(GpioContstants.GPIO_FIFTH_CHIP, GpioContstants.GPIO_RF_SWITCH_ONE, 0);
                gpioShell.setGpioValue(GpioContstants.GPIO_SECOND_CHIP, GpioContstants.GPIO_RF_SWITCH_TWO, 0);
                break;
            case 2:
                gpioShell.setGpioValue(GpioContstants.GPIO_FIFTH_CHIP, GpioContstants.GPIO_RF_SWITCH_ONE, 0);
                gpioShell.setGpioValue(GpioContstants.GPIO_SECOND_CHIP, GpioContstants.GPIO_RF_SWITCH_TWO, 1);
                break;
            case 3:
                gpioShell.setGpioValue(GpioContstants.GPIO_FIFTH_CHIP, GpioContstants.GPIO_RF_SWITCH_ONE, 1);
                gpioShell.setGpioValue(GpioContstants.GPIO_SECOND_CHIP, GpioContstants.GPIO_RF_SWITCH_TWO, 0);
                break;
            case 4:
                gpioShell.setGpioValue(GpioContstants.GPIO_FIFTH_CHIP, GpioContstants.GPIO_RF_SWITCH_ONE, 1);
                gpioShell.setGpioValue(GpioContstants.GPIO_SECOND_CHIP, GpioContstants.GPIO_RF_SWITCH_TWO, 1);
                break;
            default:
                break;
        }
    }

    /**
     * Initialize GPIO pins.
     */
    private void initGpioPins() {
        //Restart ThingMagic
        gpioShell.setGpioValue(GpioContstants.GPIO_THIRD_CHIP, GpioContstants.GPIO_RFID_ENABLE_LINE, 1);
        gpioShell.setGpioValue(GpioContstants.GPIO_THIRD_CHIP, GpioContstants.GPIO_RFID_ENABLE_LINE, 0);
    }

}
